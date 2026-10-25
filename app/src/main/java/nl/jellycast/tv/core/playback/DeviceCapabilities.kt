package nl.jellycast.tv.core.playback

/** Codec names are the Jellyfin/FFmpeg names (`hevc`, `h264`, `eac3`, `truehd`, …). */
data class DeviceCapabilities(
    /** Codecs with a hardware decoder; software decoders are too slow for HD. */
    val videoCodecs: Set<String>,
    val hevcMain10: Boolean,
    val avcHigh10: Boolean,
    val maxVideoWidth: Int,
    val maxVideoHeight: Int,
    /** Supported by both the decoder and the display. */
    val hdrTypes: Set<HdrType>,
    val dolbyVisionProfiles: Set<Int>,
    val passthroughAudio: Set<String>,
    /** Platform decoders plus FFmpeg. */
    val decodableAudio: Set<String>,
    val maxPcmChannels: Int,
    /** Server (ffprobe) scale: H.264 41 = 4.1, HEVC 153 = 5.1. A missing codec has no limit. */
    val maxVideoLevels: Map<String, Int> = emptyMap(),
    /** For the codecs other than HEVC; [maxVideoWidth] applies to HEVC. */
    val maxVideoSizes: Map<String, VideoSize> = emptyMap(),
    val vp9Profile2: Boolean = false,
    val av1Main10: Boolean = false,
    val audioOutput: AudioOutput = AudioOutput.Unknown,
    val dolbyVisionHdr10PlusBroken: Boolean = false,
)

data class VideoSize(val width: Int, val height: Int)

/** ARC cannot carry lossless audio (TrueHD, DTS-HD MA); eARC can. */
enum class AudioOutput { Hdmi, HdmiArc, HdmiEarc, Bluetooth, Speaker, Usb, Unknown }

fun interface CapabilitiesReader {
    /** Blocks briefly (decoder list, audio output): call on the io dispatcher. */
    fun read(): DeviceCapabilities
}

enum class HdrType { Hdr10, Hdr10Plus, Hlg, DolbyVision }

val LosslessAudioCodecs = setOf("truehd", "mlp", "dts", "dca")

private const val SURROUND_PCM_CHANNELS = 6

/**
 * Decoding TrueHD/DTS locally only helps with surround PCM or without AC3/EAC3 passthrough. A
 * soundbar on plain ARC (stereo PCM, but AC3/EAC3 passthrough) is better off with server EAC3 5.1.
 */
fun decodesLosslessLocally(capabilities: DeviceCapabilities, settings: PlaybackSettings): Boolean =
    settings.decodeLosslessAudioLocally &&
        (
            capabilities.maxPcmChannels >= SURROUND_PCM_CHANNELS ||
                capabilities.passthroughAudio.none { it == "eac3" || it == "ac3" }
            )

data class PlaybackSettings(
    val decodeLosslessAudioLocally: Boolean = true,
    val maxStreamingBitrate: Int = DEFAULT_MAX_BITRATE,
    val frameRateMode: FrameRateMode = FrameRateMode.Always,
    /** A missing format means [FormatSupport.Auto]. */
    val formatSupport: Map<PlaybackFormat, FormatSupport> = emptyMap(),
    /** Full styling, but the server transcodes; off = Media3 renders them simplified. */
    val burnInAss: Boolean = false,
    val subtitleSize: SubtitleSize = SubtitleSize.Normal,
    val subtitleBackground: Boolean = false,
    val downmixToStereo: Boolean = false,
    /** For chains that still click or drop out after a mode switch (usually an amplifier). */
    val startDelayMs: Int = 0,
    val tunneling: FormatSupport = FormatSupport.Auto,
    /** Some decoders report a lower level than they handle, so the server transcodes needlessly. */
    val ignoreVideoLevel: Boolean = false,
)

enum class FrameRateMode {
    /** Also non-seamless: the Chromecast never switches seamlessly and defaults to seamless. */
    Always,

    /** Follows the system "Match frame rate" setting (Never, Seamless, Always). */
    System,

    Off,
}

enum class PlaybackFormat(val hdrType: HdrType?, val codecs: Set<String>) {
    Hdr10(HdrType.Hdr10, emptySet()),
    Hdr10Plus(HdrType.Hdr10Plus, emptySet()),
    DolbyVision(HdrType.DolbyVision, emptySet()),
    Ac3(null, setOf("ac3")),
    Eac3(null, setOf("eac3")),
    Dts(null, setOf("dts", "dca")),
    TrueHd(null, setOf("truehd", "mlp")),
}

/** Auto = what the device reports; On/Off override it, because HDMI chains often misreport. */
enum class FormatSupport {
    Auto,
    On,
    Off,
    ;

    fun next(): FormatSupport = entries[(ordinal + 1) % entries.size]
}

/** [scale] is relative to Media3's default subtitle size. */
enum class SubtitleSize(val scale: Float) {
    Small(0.8f),
    Normal(1f),
    Large(1.25f),
}

/** Well above the heaviest files; a lower limit makes the server refuse direct play. */
const val DEFAULT_MAX_BITRATE = 200_000_000

val MaxBitrateOptions = listOf(DEFAULT_MAX_BITRATE, 80_000_000, 40_000_000, 20_000_000, 10_000_000)

/** Cycles; an unknown value starts again at no limit. */
fun nextMaxBitrate(current: Int): Int {
    val index = MaxBitrateOptions.indexOf(current)
    return MaxBitrateOptions[(index + 1) % MaxBitrateOptions.size]
}
