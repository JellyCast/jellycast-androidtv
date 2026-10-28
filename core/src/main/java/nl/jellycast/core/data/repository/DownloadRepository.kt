package nl.jellycast.core.data.repository

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.datastore.SessionStore
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile
import org.jellyfin.sdk.model.serializer.toUUID

/** What the server knows about the file of one item, for the download choice. */
data class DownloadSource(
    val mediaSourceId: String,
    val sizeBytes: Long?,
    /** "4K", "HDR10", "Dolby Atmos", "PGS": the tech line of the original. */
    val techParts: List<String>,
)

/** A running HLS transcode for a Compact download; [playlistUrl] is the master playlist. */
data class CompactTranscode(val playlistUrl: String, val playSessionId: String)

data class ServerUserData(val positionTicks: Long, val played: Boolean, val lastPlayedAtMs: Long?)

/** The server answered, but not with success; unlike an [IOException] waiting won't help. */
class DownloadServerException(val status: Int) : Exception("The server answered $status")

/**
 * Server access for the phone app's downloads. Network trouble is an [IOException], a refusal
 * a [DownloadServerException]. The access token stays in here.
 */
interface DownloadRepository {
    suspend fun getSource(itemId: String): DownloadSource?

    suspend fun startCompactTranscode(
        itemId: String,
        mediaSourceId: String,
        maxWidth: Int,
        maxBitsPerSecond: Int,
    ): CompactTranscode

    suspend fun stopTranscode(playSessionId: String)

    suspend fun fetchText(url: String): String

    suspend fun fetchBytes(url: String, timeoutMs: Long): ByteArray

    /**
     * Continues the original file into [target] from its current length (starting over when the
     * server ignores the range). Returns the file extension the server names.
     */
    suspend fun downloadOriginal(
        itemId: String,
        target: File,
        onProgress: (bytes: Long, totalBytes: Long?) -> Unit,
    ): String

    suspend fun getUserData(itemId: String): ServerUserData

    /** Keeps [lastPlayedAtMs] as given, unlike a playback report, which stamps it with now. */
    suspend fun setUserData(
        itemId: String,
        positionTicks: Long,
        played: Boolean,
        lastPlayedAtMs: Long,
    )

    suspend fun isServerReachable(): Boolean
}

class DefaultDownloadRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val http: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : DownloadRepository {
    override suspend fun getSource(itemId: String): DownloadSource? = call {
        api.userLibraryApi
            .getItem(itemId = itemId.toUUID(), userId = userId())
            .content.mediaSources
            ?.firstOrNull()
            ?.toDownloadSource()
    }

    override suspend fun startCompactTranscode(
        itemId: String,
        mediaSourceId: String,
        maxWidth: Int,
        maxBitsPerSecond: Int,
    ): CompactTranscode = call {
        val response =
            api.mediaInfoApi
                .getPostedPlaybackInfo(
                    itemId.toUUID(),
                    PlaybackInfoDto(
                        userId = userId(),
                        maxStreamingBitrate = maxBitsPerSecond,
                        subtitleStreamIndex = NO_SUBTITLE,
                        mediaSourceId = mediaSourceId,
                        deviceProfile = compactProfile(maxBitsPerSecond),
                        enableDirectPlay = false,
                        enableDirectStream = false,
                        enableTranscoding = true,
                        allowVideoStreamCopy = false,
                    ),
                ).content
        val path =
            response.mediaSources.firstOrNull { it.id == mediaSourceId }?.transcodingUrl
                ?: throw DownloadServerException(0)
        CompactTranscode(
            playlistUrl = api.baseUrl.orEmpty().trimEnd('/') + path + "&MaxWidth=$maxWidth",
            playSessionId = response.playSessionId.orEmpty(),
        )
    }

    override suspend fun stopTranscode(playSessionId: String) {
        call {
            api.hlsSegmentApi.stopEncodingProcess(
                deviceId = api.deviceInfo.id,
                playSessionId = playSessionId,
            )
        }
    }

    override suspend fun fetchText(url: String): String =
        String(fetchBytes(url, PLAYLIST_TIMEOUT_MS), Charsets.UTF_8)

    override suspend fun fetchBytes(url: String, timeoutMs: Long): ByteArray =
        withContext(dispatchers.io) {
            val client = http.newBuilder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
            execute(client.newCall(authorized(url).build())) { response ->
                response.checkSuccess()
                response.requireBody().bytes()
            }
        }

    override suspend fun downloadOriginal(
        itemId: String,
        target: File,
        onProgress: (bytes: Long, totalBytes: Long?) -> Unit,
    ): String = withContext(dispatchers.io) {
        val start = if (target.exists()) target.length() else 0L
        val url = api.baseUrl.orEmpty().trimEnd('/') + "/Items/$itemId/Download"
        val request = authorized(url).apply { if (start > 0) header("Range", "bytes=$start-") }
        val client = http.newBuilder().readTimeout(ORIGINAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        execute(client.newCall(request.build())) { response ->
            response.checkSuccess()
            val resumed = response.code == HTTP_PARTIAL
            val offset = if (resumed) start else 0L
            val length = response.requireBody().contentLength().takeIf { it >= 0 }
            val total = totalFromRange(response.header("Content-Range"))
                ?: length?.plus(offset)
            var written = offset
            FileOutputStream(target, resumed).use { output ->
                val input = response.requireBody().byteStream()
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    onProgress(written, total)
                }
            }
            if (total != null && written < total) throw IOException("Connection ended early")
            extensionFrom(response.header("Content-Disposition")) ?: DEFAULT_EXTENSION
        }
    }

    override suspend fun getUserData(itemId: String): ServerUserData = call {
        val data = api.itemsApi.getItemUserData(itemId = itemId.toUUID(), userId = userId())
            .content
        ServerUserData(
            positionTicks = data.playbackPositionTicks,
            played = data.played,
            lastPlayedAtMs = data.lastPlayedDate?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        )
    }

    override suspend fun setUserData(
        itemId: String,
        positionTicks: Long,
        played: Boolean,
        lastPlayedAtMs: Long,
    ) {
        call {
            api.itemsApi.updateItemUserData(
                itemId = itemId.toUUID(),
                userId = userId(),
                data =
                    UpdateUserItemDataDto(
                        playbackPositionTicks = positionTicks,
                        played = played,
                        lastPlayedDate =
                            LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(lastPlayedAtMs),
                                ZoneOffset.UTC,
                            ),
                    ),
            )
        }
    }

    override suspend fun isServerReachable(): Boolean = withContext(dispatchers.io) {
        val url = api.baseUrl?.trimEnd('/') ?: return@withContext false
        val client =
            http.newBuilder().callTimeout(SERVER_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
        try {
            client.newCall(Request.Builder().url("$url/System/Info/Public").build()).execute()
                .use { it.isSuccessful }
        } catch (_: IOException) {
            false
        }
    }

    private fun authorized(url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", authorizationHeader())

    private fun authorizationHeader(): String = AuthorizationHeaderBuilder.buildHeader(
        clientName = api.clientInfo.name,
        clientVersion = api.clientInfo.version,
        deviceId = api.deviceInfo.id,
        deviceName = api.deviceInfo.name,
        accessToken = api.accessToken,
    )

    private suspend fun userId(): UUID =
        requireNotNull(store.session.first().userId) { "Not logged in" }.toUUID()

    private suspend fun <T> call(block: suspend () -> T): T = withContext(dispatchers.io) {
        try {
            block()
        } catch (e: InvalidStatusException) {
            throw DownloadServerException(e.status)
        } catch (e: ApiClientException) {
            throw IOException(e.message, e)
        }
    }
}

/** Cancelling the coroutine cancels the call, so a blocked read ends at once. */
private suspend fun <T> execute(call: Call, block: suspend (Response) -> T): T {
    val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
    try {
        return call.execute().use { block(it) }
    } finally {
        handle?.dispose()
    }
}

private fun Response.requireBody(): ResponseBody = body ?: throw IOException("No body")

private fun Response.checkSuccess() {
    if (!isSuccessful) throw DownloadServerException(code)
}

/** The server's 1080p HEVC transcode in fMP4 segments, stereo AAC, no subtitles. */
private fun compactProfile(maxBitsPerSecond: Int) = buildDeviceProfile {
    name = "JellyCast Compact"
    maxStreamingBitrate = maxBitsPerSecond
    transcodingProfile {
        type = DlnaProfileType.VIDEO
        context = EncodingContext.STREAMING
        protocol = MediaStreamProtocol.HLS
        container = "mp4"
        videoCodec("hevc")
        audioCodec("aac")
        maxAudioChannels = "2"
        minSegments = 1
        breakOnNonKeyFrames = true
    }
    subtitleProfile("vtt", SubtitleDeliveryMethod.HLS)
}

internal fun MediaSourceInfo.toDownloadSource(): DownloadSource = DownloadSource(
    mediaSourceId = id.orEmpty(),
    sizeBytes = size,
    techParts = techParts(mediaStreams.orEmpty()),
)

internal fun techParts(streams: List<MediaStream>): List<String> {
    val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio =
        streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault }
            ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
    val subtitleCodecs =
        streams.filter { it.type == MediaStreamType.SUBTITLE }
            .mapNotNull { it.codec?.lowercase() }
    return listOfNotNull(
        video?.height?.let(::resolutionName),
        video?.videoRangeType?.serialName?.let(::rangeName),
        audio?.let(::audioName),
        "PGS".takeIf { "pgssub" in subtitleCodecs },
    )
}

private fun resolutionName(height: Int): String? = when {
    height > UHD_MIN_HEIGHT -> "4K"
    height > FULL_HD_MIN_HEIGHT -> "1080p"
    height > 0 -> "${height}p"
    else -> null
}

private fun rangeName(type: String): String? = when {
    type.startsWith("DOVI") -> "Dolby Vision"
    type == "HDR10Plus" -> "HDR10+"
    type == "HDR10" -> "HDR10"
    type == "HLG" -> "HLG"
    else -> null
}

private fun audioName(stream: MediaStream): String? {
    val profile = stream.profile.orEmpty()
    val atmos = "Atmos" in profile
    val channels = stream.channels ?: 2
    return when (stream.codec?.lowercase()) {
        "truehd" -> if (atmos) "TrueHD Atmos" else "TrueHD"

        "eac3" -> if (atmos) "Dolby Atmos" else "Dolby Digital+"

        "ac3" -> "Dolby Digital"

        "dts" -> when {
            "MA" in profile -> "DTS-HD MA"
            ":X" in profile -> "DTS:X"
            else -> "DTS"
        }

        "aac" -> if (channels > 2) "AAC ${channels}ch" else null

        "flac" -> "FLAC"

        else -> null
    }
}

/** "bytes 100-199/3046766098" → 3046766098. */
internal fun totalFromRange(header: String?): Long? =
    header?.substringAfterLast('/', "")?.toLongOrNull()

/** `attachment; filename=Movie.2024.mkv; …` → "mkv". */
internal fun extensionFrom(disposition: String?): String? {
    val name =
        disposition?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("filename=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim('"')
            ?: return null
    return name.substringAfterLast('.', "").lowercase().takeIf {
        it.isNotEmpty() && it.length <= MAX_EXTENSION_LENGTH && it.all(Char::isLetterOrDigit)
    }
}

private const val HTTP_PARTIAL = 206
private const val BUFFER_BYTES = 256 * 1024
private const val PLAYLIST_TIMEOUT_MS = 60_000L
private const val ORIGINAL_TIMEOUT_MS = 120_000L
private const val SERVER_CHECK_TIMEOUT_MS = 5_000L
private const val UHD_MIN_HEIGHT = 1100
private const val FULL_HD_MIN_HEIGHT = 730
private const val MAX_EXTENSION_LENGTH = 5
private const val DEFAULT_EXTENSION = "mkv"
