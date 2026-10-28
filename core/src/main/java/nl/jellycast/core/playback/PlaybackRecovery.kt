package nl.jellycast.core.playback

import androidx.media3.common.PlaybackException
import nl.jellycast.core.model.PlayMethod

/** Coarse enough to pick a fallback step. */
enum class PlaybackFailure {
    Network,

    /** Media3 cannot read the file (container, parser). */
    Source,

    /** The audio output or audio decoder refuses, e.g. an AVR refusing a passthrough format. */
    Audio,

    /** The video decoder fails, e.g. one that overstates a profile. */
    Video,

    /** The network cannot keep up with the bitrate. */
    TooSlow,

    Other,
}

/**
 * How the next playback plan backs off after an error. Each step costs the server more work and is
 * never undone while the same item plays.
 */
data class PlaybackFallback(
    /** Off = the server remuxes. */
    val directPlay: Boolean = true,
    /** Off = the server transcodes the video too; heavy for the server, so the last step. */
    val videoCopy: Boolean = true,
    /** Off = the device decodes everything to PCM. */
    val passthrough: Boolean = true,
    /** On = the server delivers stereo AAC, which every output handles. */
    val stereo: Boolean = false,
    val tunneling: Boolean = true,
    /** Cap after slow loading (`null` = the setting); the server then transcodes. */
    val maxBitrate: Int? = null,
)

val SlowNetworkBitrates = listOf(40_000_000, 20_000_000, 10_000_000)

sealed interface PlaybackRecovery {
    /** Same plan with a new player, e.g. after the system reclaimed the decoder. */
    data object Retry : PlaybackRecovery

    data class Fallback(val fallback: PlaybackFallback) : PlaybackRecovery

    data object GiveUp : PlaybackRecovery
}

/** Only real connection errors; other IO errors count as source errors, where a remux helps. */
private val NetworkErrorCodes =
    setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )

private val AudioOutputErrorCodes =
    setOf(
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED,
    )

private val DecoderErrorCodes =
    setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
    )

/**
 * [isSource]/[isRenderer] come from `ExoPlaybackException.type`, [rendererMimeType] from its
 * `rendererFormat`; plain values instead of the exception keep this testable on the JVM.
 */
fun classifyPlaybackFailure(
    errorCode: Int,
    isSource: Boolean,
    isRenderer: Boolean,
    rendererMimeType: String?,
): PlaybackFailure = when {
    errorCode in NetworkErrorCodes -> PlaybackFailure.Network

    isSource -> PlaybackFailure.Source

    errorCode in AudioOutputErrorCodes -> PlaybackFailure.Audio

    isRenderer && errorCode in DecoderErrorCodes ->
        when {
            rendererMimeType?.startsWith("audio/") == true -> PlaybackFailure.Audio
            rendererMimeType?.startsWith("video/") == true -> PlaybackFailure.Video
            else -> PlaybackFailure.Other
        }

    else -> PlaybackFailure.Other
}

/**
 * [retryUsed]: the same plan was already retried since playback was last stable, so a recurring
 * error ends on the error screen instead of looping. A tunneled plan first drops tunneling, which
 * is unreliable across devices (jellyfin-androidtv disabled it for that reason, jf#3295).
 */
fun recoverFrom(
    failure: PlaybackFailure,
    fallback: PlaybackFallback,
    playMethod: PlayMethod,
    retryUsed: Boolean,
    tunneled: Boolean = false,
): PlaybackRecovery = when {
    tunneled && fallback.tunneling && failure.mayBeTunneling() ->
        PlaybackRecovery.Fallback(fallback.copy(tunneling = false))

    else -> recoverWithoutTunneling(failure, fallback, playMethod, retryUsed)
}

private fun PlaybackFailure.mayBeTunneling() =
    this == PlaybackFailure.Audio || this == PlaybackFailure.Video || this == PlaybackFailure.Other

private fun recoverWithoutTunneling(
    failure: PlaybackFailure,
    fallback: PlaybackFallback,
    playMethod: PlayMethod,
    retryUsed: Boolean,
): PlaybackRecovery = when (failure) {
    PlaybackFailure.Network ->
        if (retryUsed) PlaybackRecovery.GiveUp else PlaybackRecovery.Retry

    PlaybackFailure.Source ->
        when {
            playMethod == PlayMethod.DirectPlay && fallback.directPlay ->
                PlaybackRecovery.Fallback(fallback.copy(directPlay = false))

            fallback.videoCopy -> PlaybackRecovery.Fallback(fallback.withoutVideoCopy())

            else -> PlaybackRecovery.GiveUp
        }

    PlaybackFailure.Audio ->
        when {
            fallback.passthrough -> PlaybackRecovery.Fallback(fallback.copy(passthrough = false))
            !fallback.stereo -> PlaybackRecovery.Fallback(fallback.copy(stereo = true))
            else -> PlaybackRecovery.GiveUp
        }

    PlaybackFailure.Video ->
        when {
            !retryUsed -> PlaybackRecovery.Retry
            fallback.videoCopy -> PlaybackRecovery.Fallback(fallback.withoutVideoCopy())
            else -> PlaybackRecovery.GiveUp
        }

    PlaybackFailure.TooSlow ->
        SlowNetworkBitrates
            .firstOrNull { it < (fallback.maxBitrate ?: Int.MAX_VALUE) }
            ?.let { PlaybackRecovery.Fallback(fallback.copy(maxBitrate = it)) }
            ?: PlaybackRecovery.GiveUp

    PlaybackFailure.Other -> PlaybackRecovery.GiveUp
}

private fun PlaybackFallback.withoutVideoCopy() = copy(directPlay = false, videoCopy = false)

fun DeviceCapabilities.limitedBy(fallback: PlaybackFallback): DeviceCapabilities =
    if (fallback.passthrough && !fallback.stereo) this else copy(passthroughAudio = emptySet())
