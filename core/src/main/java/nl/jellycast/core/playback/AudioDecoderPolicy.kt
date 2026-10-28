package nl.jellycast.core.playback

import androidx.media3.common.MimeTypes

/** Dolby formats the FFmpeg extension decodes fully multichannel. */
private val DolbySurroundMimeTypes =
    setOf(
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_TRUEHD,
    )

/**
 * Generic AC3/EAC3 decoders on TV chips often downmix surround to stereo, so Dolby formats go to
 * FFmpeg unless the platform decoder is a real Dolby one. Passthrough involves no decoder.
 */
fun allowPlatformAudioDecoder(
    mimeType: String,
    decoderName: String,
    ffmpegSupportsFormat: Boolean,
): Boolean {
    if (mimeType !in DolbySurroundMimeTypes || !ffmpegSupportsFormat) return true
    val name = decoderName.lowercase()
    return name.startsWith("c2.dolby.") || name.startsWith("omx.dolby.")
}

/** `null` for PCM and formats that never pass through. */
fun passthroughCodecFor(mimeType: String?): String? = PassthroughCodecs[mimeType]

private val PassthroughCodecs =
    mapOf(
        MimeTypes.AUDIO_AC3 to "ac3",
        MimeTypes.AUDIO_E_AC3 to "eac3",
        MimeTypes.AUDIO_E_AC3_JOC to "eac3",
        MimeTypes.AUDIO_DTS to "dts",
        MimeTypes.AUDIO_DTS_HD to "dts",
        MimeTypes.AUDIO_DTS_EXPRESS to "dts",
        // DTS:X as a literal: `MimeTypes.AUDIO_DTS_X` is still an unstable Media3 API.
        "audio/vnd.dts.uhd;profile=p2" to "dts",
        MimeTypes.AUDIO_TRUEHD to "truehd",
    )
