package nl.jellycast.core.model

// Jellyfin ticks are 100 ns.
private const val TICKS_PER_MS = 10_000L

fun Long.ticksToMs(): Long = this / TICKS_PER_MS

fun Long.msToTicks(): Long = this * TICKS_PER_MS

data class MediaItemSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val resumePositionMs: Long,
)

data class PlayRequest(
    val itemId: String,
    val startPositionMs: Long,
    val streams: StreamChoice = StreamChoice(null, null),
)

enum class PlayMethod { DirectPlay, DirectStream, Transcode }

data class AudioTrack(
    val index: Int,
    val language: String?,
    val codec: String,
    val channels: Int?,
    val title: String?,
    val isDefault: Boolean,
    val isAtmos: Boolean = false,
)

data class SubtitleTrack(
    val index: Int,
    val language: String?,
    val codec: String,
    /** The name in the file (Media3's label), not the server's display title. */
    val title: String?,
    val isDefault: Boolean,
    val isForced: Boolean,
)

data class VideoInfo(
    val codec: String,
    val width: Int,
    val height: Int,
    val frameRate: Float?,
    // Jellyfin `VideoRangeType`, e.g. `HDR10`, `DOVIWithHDR10`, `DOVIWithEL`.
    val rangeType: String,
    val dolbyVisionProfile: Int?,
    val interlaced: Boolean = false,
) {
    // The server reports interlaced 25/30 fps as the frame rate; motion judders unless the display
    // runs at the 50/60 Hz field rate (jellyfin-androidtv #4527).
    val displayFrameRate: Float?
        get() = frameRate?.let { if (interlaced && it <= MAX_INTERLACED_FRAME_RATE) it * 2 else it }

    private companion object {
        const val MAX_INTERLACED_FRAME_RATE = 30.5f
    }
}

data class PlaybackPlan(
    val itemId: String,
    val title: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val streamUrl: String,
    val playMethod: PlayMethod,
    val transcodeReasons: List<String>,
    val container: String?,
    val video: VideoInfo?,
    val audioTracks: List<AudioTrack>,
    val subtitleTracks: List<SubtitleTrack>,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val startPositionMs: Long,
    /** Listed first in the subtitle menu, in this order. */
    val preferredLanguages: List<String> = emptyList(),
    // Plays the HDR10 base layer through the HEVC decoder: for DV profile 7 when the DV decoder
    // does not advertise it (Media3 would pick the wrong one), or when Dolby Vision is off.
    val decodeDolbyVisionAsHevc: Boolean = false,
    // Jellyfin codec names sent as bitstream. `null` = whatever the output supports; empty =
    // decode everything locally.
    val passthroughAudio: Set<String>? = null,
    val allowTunneling: Boolean = true,
    val outcome: PlaybackOutcome? = null,
    val bitrateLimit: BitrateLimit? = null,
)

/**
 * The audio and subtitle stream that play now; a remote command can change them. A subtitle of
 * [RemoteCommand.NO_STREAM] or `null` is off.
 */
data class StreamChoice(val audioStreamIndex: Int?, val subtitleStreamIndex: Int?)

fun PlaybackPlan.streamChoice() = StreamChoice(audioStreamIndex, subtitleStreamIndex)

/** A cap below "no limit"; a file above it is transcoded (`ContainerBitrateExceedsLimit`). */
data class BitrateLimit(val bitsPerSecond: Int, val cause: BitrateLimitCause)

enum class BitrateLimitCause {
    QualitySetting,

    /** Set by the fallback after the stream kept running dry. */
    SlowNetwork,
}

data class PlaybackOutcome(
    val video: VideoOutcome?,
    val audio: AudioOutcome?,
    val serverReasons: List<String>,
)

data class VideoOutcome(val source: VideoInfo, val result: VideoResult)

enum class VideoResult {
    AsIs,

    DolbyVisionBaseLayer,

    ConvertedByServer,
}

/**
 * [source] is the file's default track; [played] may be another track of the same file (e.g. EAC3
 * next to TrueHD).
 */
data class AudioOutcome(
    val source: AudioTrack,
    val played: AudioTrack,
    val result: AudioResult,
    // Only for [AudioResult.DecodedLocally].
    val pcmChannels: Int? = null,
) {
    // Only an Atmos bitstream keeps Atmos.
    val losesAtmos: Boolean
        get() = source.isAtmos && !(played.isAtmos && result == AudioResult.Passthrough)
}

enum class AudioResult {
    Passthrough,

    DecodedLocally,

    ConvertedByServer,
}

/** `1:23:45` of `12:34`. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

data class PlaybackTech(
    val playMethod: PlayMethod,
    val audio: AudioTrack?,
    // The default track the app replaced because its lossless codec cannot pass through.
    val replacedAudio: AudioTrack?,
    val outcome: PlaybackOutcome? = null,
)
