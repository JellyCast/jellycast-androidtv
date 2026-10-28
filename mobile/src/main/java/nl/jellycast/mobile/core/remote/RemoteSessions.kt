package nl.jellycast.mobile.core.remote

import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.jellycast.core.common.Clock
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvLink
import nl.jellycast.mobile.core.remote.androidtv.remotePlayLink

data class RemoteState(
    val sessions: List<RemoteSession> = emptyList(),
    /** TV apps that take a "play" right now. */
    val devices: List<RemoteDevice> = emptyList(),
    val paired: List<PairedTv> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    /** Paired TVs the phone reached directly, checked when the Play-on sheet opens. */
    val reachableTvs: Set<String> = emptySet(),
    val hasCheckedReachability: Boolean = false,
    val commandFailed: Boolean = false,
    /** The TV being woken and started right now, for a status on screen. */
    val waking: String? = null,
    /** The session the now-playing screen shows; it stays open when that playback stops. */
    val shownSessionId: String? = null,
    /** The TV behind [shownSessionId]: its next episode can come back under another session. */
    val shownDeviceId: String? = null,
    /** When the server last answered a poll; tells a fresh answer from an older one. */
    val refreshedAtMs: Long? = null,
) {
    /** The one the TV bar shows: the most recently reported. */
    val current: RemoteSession? get() = sessions.firstOrNull()

    fun session(id: String?): RemoteSession? = sessions.firstOrNull { it.id == id }

    fun pairedTv(deviceId: String): PairedTv? = paired.firstOrNull { it.deviceId == deviceId }

    fun isPaired(deviceId: String): Boolean = pairedTv(deviceId) != null

    /** Paired TVs whose JellyCast isn't open: they can only be woken. */
    val sleepingTvs: List<PairedTv>
        get() =
            paired
                .filter { tv -> devices.none { it.deviceId == tv.deviceId } }
                .map { it.copy(name = names[it.deviceId] ?: it.name) }

    val wakeableTvs: List<PairedTv> get() = sleepingTvs.filter { it.deviceId in reachableTvs }

    /**
     * TVs to offer with JellyCast open. A paired one always goes through the remote route,
     * which needs a direct connection, so it only shows once the probe reached it.
     */
    val playableDevices: List<RemoteDevice>
        get() = devices.filter { !isPaired(it.deviceId) || it.deviceId in reachableTvs }

    /** After the probe: a paired TV that can't be reached from this network. */
    val hasUnreachablePairedTv: Boolean
        get() = hasCheckedReachability && paired.any { it.deviceId !in reachableTvs }

    /** Android TVs with JellyCast open that aren't paired yet. */
    val pairableTvs: List<RemoteDevice>
        get() = devices.filter { it.isAndroidTv && it.address != null && !isPaired(it.deviceId) }

    /** Something to offer in "Play on…": a TV with JellyCast open, or one that can be woken. */
    val hasTargets: Boolean get() = devices.isNotEmpty() || paired.isNotEmpty()
}

/**
 * What plays on the user's other devices, kept current while the app is in the foreground.
 * Holds an app scope like `PlaybackReporter`: a hand-over to or from a TV has to finish after
 * the sheet or screen that started it has closed.
 */
class RemoteSessions(
    private val api: RemoteSessionsApi,
    private val deviceStore: RemoteDeviceStore,
    private val tvLink: AndroidTvLink,
    private val clock: Clock,
    private val appScope: CoroutineScope,
) {
    private val _state = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> = _state.asStateFlow()

    private var serverSessions = emptyList<RemoteSession>()
    private var serverDevices = emptyList<RemoteDevice>()
    private val loadLock = Mutex()
    private var isLoaded = false
    private var failuresInARow = 0

    /** Polls until cancelled; run it only while the app is in the foreground. */
    suspend fun poll() = coroutineScope {
        while (isActive) {
            refresh()
            delay(PollInterval)
        }
    }

    suspend fun refresh() {
        ensureLoaded()
        val now = clock.nowMs()
        api.getSnapshot()
            .onSuccess { snapshot -> apply(snapshot, now) }
            .onFailure {
                failuresInARow++
                if (failuresInARow >= FAILURES_BEFORE_CLEARING) {
                    serverSessions = emptyList()
                    serverDevices = emptyList()
                    _state.update { it.copy(sessions = emptyList(), devices = emptyList()) }
                }
            }
    }

    private suspend fun apply(snapshot: RemoteSnapshot, nowMs: Long) {
        failuresInARow = 0
        serverSessions = snapshot.playing.filterNot { it.isSilentAt(nowMs) }
        serverDevices = snapshot.devices.filter { it.isRecentAt(nowMs) }
        val before = _state.value.paired
        // A DHCP lease can move the TV; its session tells where it is now.
        val moved =
            before.map { tv ->
                val address = serverDevices.firstOrNull { it.deviceId == tv.deviceId }?.address
                if (address != null && address != tv.host) tv.copy(host = address) else tv
            }
        _state.update {
            publish(it.copy(paired = moved, refreshedAtMs = clock.nowMs())).followingShownDevice()
        }
        if (moved != before) save()
    }

    /** The server's lists with the names the user gave on this phone. */
    private fun publish(state: RemoteState): RemoteState = state.copy(
        sessions = serverSessions.map {
            it.copy(
                deviceName =
                    state.names[it.deviceId] ?: it.deviceName,
            )
        },
        devices = serverDevices.map { it.copy(name = state.names[it.deviceId] ?: it.name) },
    )

    fun show(session: RemoteSession) {
        _state.update { it.copy(shownSessionId = session.id, shownDeviceId = session.deviceId) }
    }

    fun hideNowPlaying() {
        _state.update { it.copy(shownSessionId = null, shownDeviceId = null) }
    }

    fun dismissCommandFailure() {
        _state.update { it.copy(commandFailed = false) }
    }

    /** Sends in the app scope, so it also arrives when the screen closes right after. */
    fun launchSend(command: RemoteCommand, sessionId: String) {
        appScope.launch { send(command, sessionId) }
    }

    /** Shows the result right away and corrects it with the TV's own report shortly after. */
    suspend fun send(command: RemoteCommand, sessionId: String) {
        applyLocally(command, sessionId)
        api.send(command, sessionId).onFailure { markFailed() }
        delay(ReportDelay)
        refresh()
        // Between two items the TV has stopped one and not yet started the next.
        if (command.changesItem && _state.value.session(sessionId) == null) {
            delay(NextItemDelay)
            refresh()
        }
    }

    /** After "Continue on this phone": stop the TV, and turn a paired Chromecast off. */
    fun launchHandBack(session: RemoteSession) {
        appScope.launch {
            send(RemoteCommand.Stop, session.id)
            val tv = _state.value.pairedTv(session.deviceId) ?: return@launch
            try {
                tvLink.turnOff(tv.host)
            } catch (_: IOException) {
                // The TV stopped already; leaving it on is harmless.
            }
        }
    }

    /** Plays on a TV: a paired one is woken (it may be off while JellyCast is open). */
    fun launchPlay(device: RemoteDevice, itemId: String, startMs: Long, streams: StreamChoice?) {
        val paired = _state.value.pairedTv(device.deviceId)
        if (paired != null) return launchWakeAndPlay(paired, itemId, startMs, streams)
        val command = RemoteCommand.Play(itemId, startMs, streams ?: StreamChoice(null, null))
        launchSend(command, device.id)
    }

    fun launchWakeAndPlay(tv: PairedTv, itemId: String, startMs: Long, streams: StreamChoice?) {
        appScope.launch { wakeAndPlay(tv, itemId, startMs, streams) }
    }

    suspend fun wakeAndPlay(tv: PairedTv, itemId: String, startMs: Long, streams: StreamChoice?) {
        val link =
            remotePlayLink(itemId, startMs, streams?.audioStreamIndex, streams?.subtitleStreamIndex)
        _state.update { it.copy(waking = tv.name) }
        try {
            tvLink.wakeAndOpen(tv.host, link)
        } catch (_: IOException) {
            markFailed()
        } finally {
            _state.update { it.copy(waking = null) }
        }
    }

    suspend fun checkReachability() {
        ensureLoaded()
        val paired = _state.value.paired
        val reachable =
            coroutineScope {
                paired.map { tv -> async { tv.deviceId.takeIf { tvLink.isReachable(tv.host) } } }
                    .awaitAll()
                    .filterNotNull()
                    .toSet()
            }
        _state.update { it.copy(reachableTvs = reachable, hasCheckedReachability = true) }
    }

    suspend fun addPaired(tv: PairedTv) {
        _state.update { state ->
            state.copy(
                paired = state.paired.filterNot { it.deviceId == tv.deviceId } + tv,
                reachableTvs = state.reachableTvs + tv.deviceId,
            )
        }
        save()
    }

    suspend fun forget(deviceId: String) {
        _state.update { state ->
            state.copy(
                paired = state.paired.filterNot {
                    it.deviceId ==
                        deviceId
                },
            )
        }
        save()
    }

    /** An empty name goes back to the device's own. */
    suspend fun rename(deviceId: String, name: String) {
        val trimmed = name.trim()
        _state.update { state ->
            val names =
                if (trimmed.isEmpty()) {
                    state.names - deviceId
                } else {
                    state.names +
                        (deviceId to trimmed)
                }
            publish(state.copy(names = names))
        }
        save()
    }

    /** The now-playing screen stays with its TV when that plays on under a new session. */
    private fun RemoteState.followingShownDevice(): RemoteState {
        if (shownSessionId == null || session(shownSessionId) != null) return this
        val next = sessions.firstOrNull { it.deviceId == shownDeviceId } ?: return this
        return copy(shownSessionId = next.id)
    }

    private fun applyLocally(command: RemoteCommand, sessionId: String) {
        val now = clock.nowMs()
        _state.update { state ->
            state.copy(
                sessions =
                    state.sessions.map { session ->
                        if (session.id == sessionId) session.applying(command, now) else session
                    },
            )
        }
    }

    private fun markFailed() {
        _state.update { it.copy(commandFailed = true) }
    }

    private suspend fun ensureLoaded() = loadLock.withLock {
        if (isLoaded) return@withLock
        val saved = deviceStore.load()
        _state.update { it.copy(paired = saved.paired, names = saved.names) }
        isLoaded = true
    }

    private suspend fun save() {
        val state = _state.value
        deviceStore.save(SavedRemoteDevices(state.paired, state.names))
    }

    // The server keeps a closed app's session for minutes. An open TV app reports itself every
    // minute (the WebSocket keep-alive doesn't count), so two missed reports mean closed.
    private fun RemoteDevice.isRecentAt(nowMs: Long): Boolean =
        lastActivityMs != null && nowMs - lastActivityMs < DEVICE_ACTIVITY_WINDOW_MS

    private companion object {
        val PollInterval = 5.seconds

        // The TV app reports straight after a command; the server needs a moment to have it.
        val ReportDelay = 800.milliseconds
        val NextItemDelay = 2.seconds

        // A few missed polls keep the bar: one slow answer from the server shouldn't hide it.
        const val FAILURES_BEFORE_CLEARING = 3
        const val DEVICE_ACTIVITY_WINDOW_MS = 150_000L
    }
}

private val RemoteCommand.changesItem: Boolean
    get() = this is RemoteCommand.NextEpisode || this is RemoteCommand.Play

internal fun RemoteSession.applying(command: RemoteCommand, nowMs: Long): RemoteSession {
    val moved = copy(positionMs = positionAt(nowMs), checkedInAtMs = nowMs)
    return when (command) {
        RemoteCommand.Pause -> moved.copy(isPaused = true)
        RemoteCommand.Unpause -> moved.copy(isPaused = false)
        RemoteCommand.PlayPause -> moved.copy(isPaused = !isPaused)
        is RemoteCommand.Seek -> moved.copy(positionMs = command.positionMs)
        is RemoteCommand.SetAudioStream -> moved.copy(audioStreamIndex = command.index)
        is RemoteCommand.SetSubtitleStream -> moved.copy(subtitleStreamIndex = command.index)
        RemoteCommand.Stop, RemoteCommand.NextEpisode, is RemoteCommand.Play -> moved
    }
}
