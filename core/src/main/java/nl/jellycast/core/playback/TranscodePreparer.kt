package nl.jellycast.core.playback

import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.repository.NO_SUBTITLE
import nl.jellycast.core.data.repository.PlaybackRepository
import nl.jellycast.core.data.repository.PlaybackRequest
import nl.jellycast.core.model.BitrateLimit
import nl.jellycast.core.model.BitrateLimitCause
import nl.jellycast.core.model.PlaybackPlan

/** A server transcode at a chosen size, as the phone app's quality levels ask for. */
data class TranscodeTarget(
    val maxBitrate: Int,
    val maxWidth: Int,
    val audioStreamIndex: Int?,
    /** `null` = no subtitle. */
    val subtitleStreamIndex: Int?,
)

fun interface TranscodePlanner {
    suspend fun prepare(
        itemId: String,
        startPositionMs: Long,
        settings: PlaybackSettings,
        target: TranscodeTarget,
    ): PlaybackPlan
}

/**
 * The server converts the video (no direct play, no stream copy) within [TranscodeTarget]; the
 * chosen subtitle goes to the server, which delivers or burns it into the stream.
 */
class TranscodePreparer(
    private val capabilitiesReader: CapabilitiesReader,
    private val repository: PlaybackRepository,
    private val dispatchers: AppDispatchers,
) : TranscodePlanner {
    override suspend fun prepare(
        itemId: String,
        startPositionMs: Long,
        settings: PlaybackSettings,
        target: TranscodeTarget,
    ): PlaybackPlan {
        val capabilities =
            withContext(dispatchers.io) { capabilitiesReader.read() }.withOverrides(settings)
        val plan =
            repository.getPlaybackPlan(
                PlaybackRequest(
                    itemId = itemId,
                    deviceProfile = createDeviceProfile(capabilities, settings),
                    maxStreamingBitrate = target.maxBitrate,
                    audioStreamIndex = target.audioStreamIndex,
                    subtitleStreamIndex = target.subtitleStreamIndex ?: NO_SUBTITLE,
                    startPositionMs = startPositionMs,
                    allowDirectPlay = false,
                    allowVideoCopy = false,
                ),
            )
        return plan.copy(
            streamUrl = plan.streamUrl.withMaxWidth(target.maxWidth),
            bitrateLimit = BitrateLimit(target.maxBitrate, BitrateLimitCause.QualitySetting),
            passthroughAudio = playerPassthrough(capabilities, settings, PlaybackFallback()),
        )
    }
}

internal fun String.withMaxWidth(maxWidth: Int): String {
    val separator = if ('?' in this) "&" else "?"
    return "$this${separator}MaxWidth=$maxWidth"
}
