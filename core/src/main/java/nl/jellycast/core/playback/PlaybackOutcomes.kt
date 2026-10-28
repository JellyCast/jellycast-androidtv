package nl.jellycast.core.playback

import nl.jellycast.core.model.AudioOutcome
import nl.jellycast.core.model.AudioResult
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackOutcome
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.VideoOutcome
import nl.jellycast.core.model.VideoResult

private fun String.isAudioReason() = startsWith("Audio") || this == "SecondaryAudioNotSupported"

/** The server only remuxes; video and audio stay unchanged. */
private const val CONTAINER_REASON = "ContainerNotSupported"

/**
 * Shows whether a transcode comes from the video or the audio. [capabilities] must be the same
 * reading (with settings and fallback) that produced the plan.
 */
fun summarizePlayback(plan: PlaybackPlan, capabilities: DeviceCapabilities): PlaybackOutcome {
    val reasons = plan.transcodeReasons
    val videoConverted =
        plan.playMethod == PlayMethod.Transcode &&
            reasons.any { !it.isAudioReason() && it != CONTAINER_REASON }
    val audioConverted =
        plan.playMethod != PlayMethod.DirectPlay && reasons.any { it.isAudioReason() }

    val video =
        plan.video?.let {
            VideoOutcome(
                source = it,
                result =
                    when {
                        videoConverted -> VideoResult.ConvertedByServer

                        it.dolbyVisionProfile != null && plan.decodeDolbyVisionAsHevc ->
                            VideoResult.DolbyVisionBaseLayer

                        else -> VideoResult.AsIs
                    },
            )
        }

    val played = plan.audioTracks.firstOrNull { it.index == plan.audioStreamIndex }
    val source = played?.let { plan.audioTracks.sourceOf(it) }
    val passthrough = plan.passthroughAudio ?: capabilities.passthroughAudio
    val audio =
        if (played == null || source == null) {
            null
        } else {
            val result =
                when {
                    audioConverted -> AudioResult.ConvertedByServer
                    played.codec.lowercase() in passthrough -> AudioResult.Passthrough
                    else -> AudioResult.DecodedLocally
                }
            AudioOutcome(
                source = source,
                played = played,
                result = result,
                pcmChannels =
                    played.channels?.coerceAtMost(capabilities.maxPcmChannels)
                        ?.takeIf { result == AudioResult.DecodedLocally },
            )
        }
    return PlaybackOutcome(video = video, audio = audio, serverReasons = reasons)
}
