package nl.jellycast.tv.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.jellycast.core.model.AudioOutcome
import nl.jellycast.core.model.AudioResult
import nl.jellycast.core.model.AudioTrack
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackOutcome
import nl.jellycast.core.model.VideoInfo
import nl.jellycast.core.model.VideoOutcome
import nl.jellycast.core.model.VideoResult
import nl.jellycast.tv.R

private val BitstreamCodecs = setOf("ac3", "eac3", "dts", "dca", "truehd", "mlp")

/** 8 → "7.1", 6 → "5.1", 2 → "2.0"; otherwise "4ch". */
fun channelLayout(channels: Int?): String? = when (channels) {
    null -> null
    8 -> "7.1"
    7 -> "6.1"
    6 -> "5.1"
    2 -> "2.0"
    1 -> "1.0"
    else -> "${channels}ch"
}

/** "truehd" → "TrueHD", "pcm_s24le" → "PCM"; other codecs in upper case. */
fun audioCodecName(codec: String): String {
    val name = codec.lowercase()
    return when {
        name == "truehd" -> "TrueHD"
        name.startsWith("pcm") -> "PCM"
        else -> codec.uppercase()
    }
}

/** "TrueHD Atmos", "EAC3 Atmos", "DTS". */
@Composable
fun audioTrackLabel(track: AudioTrack): String = audioCodecName(track.codec) +
    if (track.isAtmos) " " + stringResource(R.string.tech_atmos) else ""

/** "Dolby Vision", "HDR10", …; `null` for SDR. */
@Composable
fun sourceRangeLabel(video: VideoInfo): String? {
    val type = video.rangeType
    return when {
        type.startsWith("DOVI", ignoreCase = true) -> stringResource(R.string.tech_dolby_vision)
        type.equals("HDR10Plus", ignoreCase = true) -> stringResource(R.string.tech_hdr10_plus)
        type.equals("HDR10", ignoreCase = true) -> stringResource(R.string.tech_hdr10)
        type.equals("HLG", ignoreCase = true) -> stringResource(R.string.tech_hlg)
        else -> null
    }
}

/** "DV P7 → HDR10", or just the source range when nothing changes. */
@Composable
fun videoOutcomeShort(outcome: VideoOutcome): String? {
    val source = outcome.source
    return when (outcome.result) {
        VideoResult.DolbyVisionBaseLayer ->
            stringResource(
                R.string.tech_arrow,
                source.dolbyVisionProfile?.let { stringResource(R.string.tech_dv_profile, it) }
                    ?: stringResource(R.string.tech_dolby_vision),
                stringResource(R.string.tech_hdr10),
            )

        VideoResult.AsIs, VideoResult.ConvertedByServer -> sourceRangeLabel(source)
    }
}

/**
 * "EAC3 Atmos", "TrueHD Atmos → PCM 7.1", or "TrueHD Atmos → EAC3 Atmos track" when another
 * track from the file plays.
 */
@Composable
fun audioOutcomeShort(outcome: AudioOutcome): String {
    val source = audioTrackLabel(outcome.source)
    if (outcome.played.index != outcome.source.index) {
        return stringResource(R.string.tech_audio_replaced, source, audioTrackLabel(outcome.played))
    }
    // Every device decodes AAC, FLAC and the like itself: only show the arrow when the track
    // could have gone to the amplifier as a bitstream, or when Atmos is lost.
    val worthShowing =
        outcome.source.codec.lowercase() in BitstreamCodecs || outcome.losesAtmos
    return when (outcome.result) {
        AudioResult.DecodedLocally if !worthShowing -> source

        AudioResult.DecodedLocally ->
            stringResource(
                R.string.tech_arrow,
                source,
                stringResource(
                    R.string.tech_pcm,
                    channelLayout(outcome.pcmChannels).orEmpty(),
                ).trim(),
            )

        AudioResult.Passthrough, AudioResult.ConvertedByServer -> source
    }
}

/** What the server does, and why: video transcoded, audio transcoded, remux or direct play. */
@Composable
fun playMethodLabel(outcome: PlaybackOutcome?, playMethod: PlayMethod): String = stringResource(
    when {
        outcome?.video?.result == VideoResult.ConvertedByServer -> R.string.tech_video_converted
        outcome?.audio?.result == AudioResult.ConvertedByServer -> R.string.tech_audio_converted
        playMethod == PlayMethod.DirectPlay -> R.string.tech_direct_play
        outcome != null -> R.string.tech_remux
        playMethod == PlayMethod.DirectStream -> R.string.tech_audio_converted
        else -> R.string.tech_transcoded
    },
)
