package nl.jellycast.mobile.core.downloads

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.repository.DownloadRepository
import nl.jellycast.core.data.repository.DownloadServerException
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind
import timber.log.Timber

/** Shows the download notification while a download runs (the iOS Live Activity). */
fun interface DownloadForeground {
    fun onRunningChanged(running: Boolean)
}

/**
 * The downloads on this device and the queue: one download runs at a time, the rest wait.
 * Each item has its own folder; the list itself is saved next to them.
 */
class DownloadStore(
    private val files: DownloadFiles,
    private val repository: DownloadRepository,
    private val compact: CompactDownloader,
    private val original: OriginalDownloader,
    val offlineProgress: OfflineProgress,
    val settings: DownloadSettings,
    private val connectivity: Connectivity,
    private val library: LibraryStore,
    private val storage: StorageSpace,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val foreground: DownloadForeground,
) {
    private val _downloads = MutableStateFlow<List<Download>>(emptyList())
    val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val _rate = MutableStateFlow<DownloadRate?>(null)
    val rate: StateFlow<DownloadRate?> = _rate.asStateFlow()

    private val _isStarted = MutableStateFlow(false)
    val isStarted: StateFlow<Boolean> = _isStarted.asStateFlow()

    private val _space = MutableStateFlow<VolumeSpace?>(null)

    /** The volume's space, read again whenever the list changes. */
    val space: StateFlow<VolumeSpace?> = _space.asStateFlow()

    private val lock = Any()
    private var runningId: String? = null
    private var runningJob: Job? = null
    private var runStart: RunStart? = null
    private var lastReportedBytes = 0L

    private data class RunStart(val atMs: Long, val bytes: Long, val mediaSeconds: Double)

    /** The download that runs now. */
    val current: StateFlow<Download?> =
        downloads.map { list ->
            list.firstOrNull {
                it.status.isActive &&
                    it.status != DownloadStatus.Queued
            }
        }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The server is only reachable at home, so downloading is offered when the server check
     * answers, not by address: a host name or a VPN reaches it too.
     */
    val canReachServer: StateFlow<Boolean> get() = reachable

    private val reachable: StateFlow<Boolean> =
        connectivity.isOffline.map { !it }.stateIn(scope, SharingStarted.Eagerly, true)

    /** Picks up the downloads after a launch: interrupted ones go back in the queue. */
    fun start() {
        scope.launch {
            val saved = withContext(io) { files.loadIndex() }
            _downloads.value =
                saved.map {
                    if (it.status.isActive) it.copy(status = DownloadStatus.Queued) else it
                }
            save()
            _isStarted.value = true
            pump()
            observe()
        }
    }

    private fun observe() {
        scope.launch {
            current.map { it != null }.distinctUntilChanged().collect(foreground::onRunningChanged)
        }
        scope.launch {
            downloads.map { list -> list.map { it.id to it.status } }.distinctUntilChanged()
                .collect { _space.value = withContext(io) { storage.read() } }
        }
        scope.launch {
            connectivity.isOffline.filter { !it }.collect { sendOfflineProgress() }
        }
        scope.launch {
            combine(library.syncState, library.snapshot) { state, snapshot ->
                snapshot.takeIf { state == SyncState.Idle }
            }.distinctUntilChanged().collect { snapshot ->
                if (snapshot != null) tidy(LibraryIndex(snapshot, clock.nowMs()))
            }
        }
    }

    fun nowMs(): Long = clock.nowMs()

    /** A position watched in a download; sent at once when the server answers, else later. */
    fun recordOfflinePosition(itemId: String, positionMs: Long, finished: Boolean) {
        offlineProgress.record(itemId, positionMs, finished)
        if (canReachServer.value) scope.launch { sendOfflineProgress() }
    }

    private suspend fun sendOfflineProgress() {
        if (offlineProgress.send()) library.refresh()
    }

    fun download(itemId: String): Download? = _downloads.value.firstOrNull { it.id == itemId }

    fun localFile(itemId: String): File? =
        if (download(itemId)?.status == DownloadStatus.Done) files.localFile(itemId) else null

    /** A downloaded movie or episode, or a series with at least one downloaded episode. */
    fun isOnDevice(itemId: String): Boolean = _downloads.value.any {
        it.status == DownloadStatus.Done && (it.id == itemId || it.media.seriesId == itemId)
    }

    val usedBytes: Long get() = _downloads.value.sumOf { it.bytes }

    /** Free space minus what the queue still needs on top of what it has. */
    fun freeBytes(): Long? {
        val free = storage.read()?.freeBytes ?: return null
        val reserved =
            _downloads.value.filter { it.status != DownloadStatus.Done }
                .sumOf { (it.expectedBytes - it.bytes).coerceAtLeast(0) }
        return (free - reserved).coerceAtLeast(0)
    }

    /** What a download of this item would take, from the server's file info. */
    suspend fun plan(item: PlayableItem): PlanResult {
        val source =
            try {
                repository.getSource(item.id)
            } catch (e: IOException) {
                Timber.w(e, "No file info")
                null
            } catch (e: DownloadServerException) {
                Timber.w(e, "No file info")
                null
            }
        return PlanResult(
            DownloadPlan.of(item.runtimeMs, source?.sizeBytes),
            source?.techParts.orEmpty(),
        )
    }

    /** Asks the server again, for a tap that would otherwise be refused on an old answer. */
    suspend fun recheckServer() {
        connectivity.checkServer()
    }

    /** Seconds until the running download is done, from its speed so far. */
    fun secondsLeft(download: Download, rate: DownloadRate?): Double? {
        if (rate == null || rate.itemId != download.id) return null
        val totalSeconds = download.totalMediaSeconds
        if (download.kind == DownloadKind.Compact && totalSeconds != null &&
            rate.mediaSecondsPerSecond > 0
        ) {
            return (totalSeconds - download.mediaSeconds).coerceAtLeast(0.0) /
                rate.mediaSecondsPerSecond
        }
        val total = download.totalBytes
        if (total != null && rate.bytesPerSecond > 0) {
            return (total - download.bytes).coerceAtLeast(0) / rate.bytesPerSecond
        }
        return null
    }

    fun enqueue(item: PlayableItem, kind: DownloadKind, estimatedBytes: Long?) {
        if (download(item.id) != null) return
        val seriesPoster = (item as? PlayableItem.EpisodeItem)?.episode?.seriesId?.let {
            library.snapshot.value?.seriesById?.get(it)?.artwork?.poster
        }
        val download =
            Download(
                media = DownloadedMedia.of(item, seriesPoster),
                kind = kind,
                totalBytes = if (kind == DownloadKind.Original) estimatedBytes else null,
                estimatedBytes = estimatedBytes,
                addedAtMs = clock.nowMs(),
            )
        _downloads.update { it + download }
        save()
        pump()
    }

    fun pause(itemId: String) {
        synchronized(lock) {
            change(itemId) { it.copy(status = DownloadStatus.Paused) }
            if (runningId == itemId) stopRunning()
        }
        save()
        pump()
    }

    fun resume(itemId: String) {
        change(itemId) { it.copy(status = DownloadStatus.Queued) }
        save()
        pump()
    }

    fun pauseAll() {
        _downloads.value.filter { it.status.isActive }.forEach { pause(it.id) }
    }

    fun remove(itemId: String) {
        synchronized(lock) {
            if (runningId == itemId) stopRunning()
            _downloads.update { list -> list.filterNot { it.id == itemId } }
        }
        save()
        scope.launch(io) { files.remove(itemId) }
        pump()
    }

    private fun stopRunning() {
        runningJob?.cancel()
        runningJob = null
        runningId = null
        _rate.value = null
    }

    private fun pump() {
        synchronized(lock) {
            if (runningId != null || !_isStarted.value) return
            val next =
                _downloads.value.filter { it.status == DownloadStatus.Queued }
                    .minByOrNull { it.addedAtMs } ?: return
            runningId = next.id
            runStart = null
            lastReportedBytes = 0
            _rate.value = null
            change(next.id) { it.copy(status = DownloadStatus.Running) }
            runningJob = scope.launch { run(next) }
        }
        save()
    }

    private suspend fun run(download: Download) {
        val itemId = download.id
        while (true) {
            try {
                val size =
                    withContext(io) {
                        when (download.kind) {
                            DownloadKind.Compact -> {
                                compact.run(itemId) { compactProgressed(itemId, it) }
                                files.finishedBytes(itemId)
                            }

                            DownloadKind.Original ->
                                original.run(itemId) { bytes, total ->
                                    originalProgressed(itemId, bytes, total)
                                }
                        }
                    }
                change(itemId) { it.copy(bytes = size, totalBytes = size) }
                finish(itemId, DownloadStatus.Done)
                return
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                Timber.i(e, "Waiting for the network")
                setRunStatus(itemId, DownloadStatus.WaitingForNetwork)
                waitForServer()
                setRunStatus(itemId, DownloadStatus.Running)
                synchronized(lock) { runStart = null }
            } catch (e: DownloadServerException) {
                Timber.e(e, "Download failed")
                finish(itemId, DownloadStatus.Failed(e.message.orEmpty()))
                return
            } catch (e: IllegalStateException) {
                Timber.e(e, "Download failed")
                finish(itemId, DownloadStatus.Failed(e.message.orEmpty()))
                return
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Download failed")
                finish(itemId, DownloadStatus.Failed(e.message.orEmpty()))
                return
            }
        }
    }

    /** Checks every few seconds until the server answers again. */
    private suspend fun waitForServer() {
        while (true) {
            val online = connectivity.networkKind.value != NetworkKind.None
            if (online && repository.isServerReachable()) return
            delay(SERVER_CHECK_INTERVAL_MS)
        }
    }

    private fun compactProgressed(itemId: String, update: CompactUpdate) {
        change(itemId) {
            val estimated =
                if (update.totalMediaSeconds > 0 && update.mediaSeconds > 0) {
                    (update.bytes / update.mediaSeconds * update.totalMediaSeconds).toLong()
                } else {
                    it.estimatedBytes
                }
            it.copy(
                bytes = update.bytes,
                mediaSeconds = update.mediaSeconds,
                totalMediaSeconds = update.totalMediaSeconds,
                estimatedBytes = estimated,
            )
        }
        updateRate(itemId)
    }

    private fun originalProgressed(itemId: String, bytes: Long, totalBytes: Long?) {
        val due = synchronized(lock) {
            (bytes - lastReportedBytes >= PROGRESS_STEP_BYTES || bytes == totalBytes).also {
                if (it) lastReportedBytes = bytes
            }
        }
        if (!due) return
        change(itemId) { it.copy(bytes = bytes, totalBytes = totalBytes ?: it.totalBytes) }
        updateRate(itemId)
    }

    /**
     * Measured from the first progress of this run, which for a resumed download already
     * includes what earlier runs fetched.
     */
    private fun updateRate(itemId: String) {
        val download = download(itemId) ?: return
        val now = clock.nowMs()
        synchronized(lock) {
            if (runningId != itemId) return
            val start = runStart
            if (start == null) {
                runStart = RunStart(now, download.bytes, download.mediaSeconds)
                return
            }
            val seconds = (now - start.atMs) / MS_PER_SECOND
            if (seconds <= 1) return
            _rate.value =
                DownloadRate(
                    itemId = itemId,
                    bytesPerSecond = (download.bytes - start.bytes) / seconds,
                    mediaSecondsPerSecond = (download.mediaSeconds - start.mediaSeconds) / seconds,
                )
        }
    }

    private fun setRunStatus(itemId: String, status: DownloadStatus) {
        synchronized(lock) {
            if (runningId == itemId) change(itemId) { it.copy(status = status) }
        }
    }

    private fun finish(itemId: String, status: DownloadStatus) {
        synchronized(lock) {
            if (runningId != itemId) return
            change(itemId) { it.copy(status = status) }
            runningId = null
            runningJob = null
            _rate.value = null
        }
        save()
        pump()
    }

    private fun change(itemId: String, transform: (Download) -> Download) {
        _downloads.update { list -> list.map { if (it.id == itemId) transform(it) else it } }
    }

    private fun save() {
        scope.launch(io) { synchronized(files) { files.saveIndex(_downloads.value) } }
    }

    private companion object {
        const val SERVER_CHECK_INTERVAL_MS = 3_000L
        const val PROGRESS_STEP_BYTES = 4L * 1024 * 1024
        const val MS_PER_SECOND = 1000.0
    }
}
