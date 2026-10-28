package nl.jellycast.core.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.audio.AudioCapabilities

private val VideoMimeToCodec =
    mapOf(
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/x-vnd.on2.vp9" to "vp9",
        "video/av01" to "av1",
        "video/mpeg2" to "mpeg2video",
        "video/mp4v-es" to "mpeg4",
        "video/wvc1" to "vc1",
    )

private val AudioMimeToCodec =
    mapOf(
        "audio/mp4a-latm" to listOf("aac"),
        "audio/mpeg" to listOf("mp3"),
        "audio/flac" to listOf("flac"),
        "audio/opus" to listOf("opus"),
        "audio/vorbis" to listOf("vorbis"),
        "audio/raw" to listOf("pcm_s16le", "pcm_s24le", "pcm_s32le"),
    )

private val FfmpegAudioMimes =
    mapOf(
        MimeTypes.AUDIO_AAC to listOf("aac"),
        MimeTypes.AUDIO_MPEG to listOf("mp3"),
        MimeTypes.AUDIO_FLAC to listOf("flac"),
        MimeTypes.AUDIO_ALAC to listOf("alac"),
        MimeTypes.AUDIO_AC3 to listOf("ac3"),
        MimeTypes.AUDIO_E_AC3 to listOf("eac3"),
        MimeTypes.AUDIO_DTS to listOf("dts", "dca"),
        MimeTypes.AUDIO_TRUEHD to listOf("truehd", "mlp"),
    )

// Probed with the channel count of a real movie: Media3 refuses passthrough when the output
// reports fewer channels, so a 2-channel probe says too little.
private val PassthroughProbes =
    listOf(
        Triple(MimeTypes.AUDIO_AC3, 6, listOf("ac3")),
        Triple(MimeTypes.AUDIO_E_AC3, 6, listOf("eac3")),
        Triple(MimeTypes.AUDIO_E_AC3_JOC, 6, listOf("eac3")),
        Triple(MimeTypes.AUDIO_DTS, 6, listOf("dts", "dca")),
        Triple(MimeTypes.AUDIO_DTS_HD, 8, listOf("dts", "dca")),
        Triple(MimeTypes.AUDIO_TRUEHD, 8, listOf("truehd", "mlp")),
    )

private val DolbyVisionProfiles =
    mapOf(
        CodecProfileLevel.DolbyVisionProfileDvheDtr to 4,
        CodecProfileLevel.DolbyVisionProfileDvheStn to 5,
        CodecProfileLevel.DolbyVisionProfileDvheDtb to 7,
        CodecProfileLevel.DolbyVisionProfileDvheSt to 8,
        CodecProfileLevel.DolbyVisionProfileDvavSe to 9,
    )

// Same attributes as the player, so the probe matches what the output does for movies.
@OptIn(UnstableApi::class)
private val MovieAudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

@OptIn(UnstableApi::class)
class AndroidCapabilitiesReader(private val context: Context) : CapabilitiesReader {
    // Decoders never change while the app runs; audio output and display do, so those are read
    // per movie.
    private val decoders: DecoderCapabilities by lazy { readDecoders() }

    @Volatile private var lastDisplayHdr: Set<HdrType>? = null

    private val audioRoutes = AudioRouteMemory()

    override fun read(): DeviceCapabilities {
        val decoderCaps = decoders
        val route = routedAudioDevice()
        val audio = AudioCapabilities.getCapabilities(context, MovieAudioAttributes, route)
        val passthrough =
            PassthroughProbes
                .filter { (mime, channels, _) ->
                    val format =
                        Format
                            .Builder()
                            .setSampleMimeType(mime)
                            .setChannelCount(channels)
                            .setSampleRate(PROBE_SAMPLE_RATE_HZ)
                            .build()
                    audio.isPassthroughPlaybackSupported(format, MovieAudioAttributes)
                }.flatMap { it.third }
                .toSet()

        val output = route?.type.toAudioOutput()
        val routeCaps =
            synchronized(audioRoutes) {
                audioRoutes.stable(
                    output,
                    AudioRouteCapabilities(passthrough, audio.maxChannelCount),
                )
            }

        return DeviceCapabilities(
            videoCodecs = decoderCaps.videoCodecs,
            hevcMain10 = decoderCaps.hevcMain10,
            avcHigh10 = decoderCaps.avcHigh10,
            maxVideoWidth = decoderCaps.maxWidth,
            maxVideoHeight = decoderCaps.maxHeight,
            hdrTypes = decoderCaps.hdrTypes intersect displayHdrTypes(),
            dolbyVisionProfiles = decoderCaps.dolbyVisionProfiles,
            passthroughAudio = routeCaps.passthrough,
            decodableAudio = decoderCaps.decodableAudio,
            maxPcmChannels = routeCaps.maxPcmChannels,
            maxVideoLevels = decoderCaps.maxLevels,
            maxVideoSizes = decoderCaps.maxSizes,
            vp9Profile2 = decoderCaps.vp9Profile2,
            av1Main10 = decoderCaps.av1Main10,
            audioOutput = output,
        ).withKnownDefects(Build.MODEL)
    }

    private fun routedAudioDevice(): AudioDeviceInfo? {
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        val attributes = MovieAudioAttributes.platformAudioAttributes
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getAudioDevicesForAttributes(attributes).firstOrNull()
        } else {
            // Before Android 13 only the list of outputs is known; the first is the active route.
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull()
        }
    }

    private fun readDecoders(): DecoderCapabilities {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot {
            it.isEncoder
        }
        val hardwareVideo = decoders.filter { it.isHardwareAccelerated && !it.isSoftwareOnly }
        val hevcProfiles = hardwareVideo.profilesFor("video/hevc")
        val dvProfiles = hardwareVideo.profilesFor("video/dolby-vision").mapNotNull {
            DolbyVisionProfiles[it]
        }.toSet()
        val hevcMain10 = hevcProfiles.any { it in Main10Profiles }
        val (maxWidth, maxHeight) =
            hardwareVideo.maxSize("video/hevc") ?: hardwareVideo.maxSize("video/avc")
                ?: (FALLBACK_MAX_WIDTH to FALLBACK_MAX_HEIGHT)
        val platformAudio =
            decoders.flatMap { info ->
                info.supportedTypes.flatMap { AudioMimeToCodec[it.lowercase()].orEmpty() }
            }
        val ffmpegAudio =
            if (FfmpegLibrary.isAvailable()) {
                FfmpegAudioMimes.filterKeys { FfmpegLibrary.supportsFormat(it) }.values.flatten()
            } else {
                emptyList()
            }
        val trustedAvc = hardwareVideo.filterNot { overstatesAvcHigh10(it.name) }

        return DecoderCapabilities(
            videoCodecs =
                hardwareVideo.flatMap { info ->
                    info.supportedTypes.mapNotNull { VideoMimeToCodec[it.lowercase()] }
                }.toSet(),
            hevcMain10 = hevcMain10,
            avcHigh10 = CodecProfileLevel.AVCProfileHigh10 in trustedAvc.profilesFor("video/avc"),
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            hdrTypes =
                buildSet {
                    if (CodecProfileLevel.HEVCProfileMain10HDR10 in hevcProfiles) add(HdrType.Hdr10)
                    if (CodecProfileLevel.HEVCProfileMain10HDR10Plus in
                        hevcProfiles
                    ) {
                        add(HdrType.Hdr10Plus)
                    }
                    // HLG only counts once the display reports it too (intersected in read()).
                    if (hevcMain10) add(HdrType.Hlg)
                    if (dvProfiles.isNotEmpty()) add(HdrType.DolbyVision)
                },
            dolbyVisionProfiles = dvProfiles,
            decodableAudio = platformAudio.toSet() + ffmpegAudio,
            maxLevels =
                buildMap {
                    hardwareVideo.maxLevel("video/avc", AvcLevels)?.let { put("h264", it) }
                    hardwareVideo.maxLevel("video/hevc", HevcLevels)?.let { put("hevc", it) }
                },
            maxSizes =
                VideoMimeToCodec
                    .filterValues { it != "hevc" }
                    .mapNotNull { (mime, codec) ->
                        hardwareVideo.maxSize(mime)?.let { (w, h) -> codec to VideoSize(w, h) }
                    }.toMap(),
            vp9Profile2 =
                hardwareVideo.profilesFor("video/x-vnd.on2.vp9").any {
                    it == CodecProfileLevel.VP9Profile2 || it == CodecProfileLevel.VP9Profile2HDR
                },
            av1Main10 =
                hardwareVideo.profilesFor("video/av01").any {
                    it == CodecProfileLevel.AV1ProfileMain10 ||
                        it == CodecProfileLevel.AV1ProfileMain10HDR10
                },
        )
    }

    /**
     * Display-wide and current-mode HDR types combined: a DV TV in an SDR mode reports nothing for
     * the mode alone. A missing or sleeping display keeps the last reading: silence is not SDR.
     */
    private fun displayHdrTypes(): Set<HdrType> {
        // The application context has no display; ask for the main (HDMI) display.
        val display = context.getSystemService(
            DisplayManager::class.java,
        )?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null || display.state != Display.STATE_ON) {
            return lastDisplayHdr.orEmpty()
        }
        @Suppress("DEPRECATION") // The per-mode replacement misses what other modes support.
        val displayWide = display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        val currentMode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                display.mode?.supportedHdrTypes ?: IntArray(0)
            } else {
                IntArray(0)
            }
        return (displayWide.toSet() + currentMode.toSet())
            .mapNotNull {
                when (it) {
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> HdrType.Hdr10
                    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> HdrType.Hdr10Plus
                    Display.HdrCapabilities.HDR_TYPE_HLG -> HdrType.Hlg
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> HdrType.DolbyVision
                    else -> null
                }
            }.toSet()
            .also { lastDisplayHdr = it }
    }

    private companion object {
        val Main10Profiles =
            setOf(
                CodecProfileLevel.HEVCProfileMain10,
                CodecProfileLevel.HEVCProfileMain10HDR10,
                CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            )

        // Android level → ffprobe scale (level × 10).
        val AvcLevels =
            mapOf(
                CodecProfileLevel.AVCLevel3 to 30,
                CodecProfileLevel.AVCLevel31 to 31,
                CodecProfileLevel.AVCLevel32 to 32,
                CodecProfileLevel.AVCLevel4 to 40,
                CodecProfileLevel.AVCLevel41 to 41,
                CodecProfileLevel.AVCLevel42 to 42,
                CodecProfileLevel.AVCLevel5 to 50,
                CodecProfileLevel.AVCLevel51 to 51,
                CodecProfileLevel.AVCLevel52 to 52,
                CodecProfileLevel.AVCLevel6 to 60,
                CodecProfileLevel.AVCLevel61 to 61,
                CodecProfileLevel.AVCLevel62 to 62,
            )

        // Android level → ffprobe scale (level × 30); High tier counts as the same level.
        val HevcLevels =
            mapOf(
                CodecProfileLevel.HEVCMainTierLevel4 to 120,
                CodecProfileLevel.HEVCHighTierLevel4 to 120,
                CodecProfileLevel.HEVCMainTierLevel41 to 123,
                CodecProfileLevel.HEVCHighTierLevel41 to 123,
                CodecProfileLevel.HEVCMainTierLevel5 to 150,
                CodecProfileLevel.HEVCHighTierLevel5 to 150,
                CodecProfileLevel.HEVCMainTierLevel51 to 153,
                CodecProfileLevel.HEVCHighTierLevel51 to 153,
                CodecProfileLevel.HEVCMainTierLevel52 to 156,
                CodecProfileLevel.HEVCHighTierLevel52 to 156,
                CodecProfileLevel.HEVCMainTierLevel6 to 180,
                CodecProfileLevel.HEVCHighTierLevel6 to 180,
                CodecProfileLevel.HEVCMainTierLevel61 to 183,
                CodecProfileLevel.HEVCHighTierLevel61 to 183,
                CodecProfileLevel.HEVCMainTierLevel62 to 186,
                CodecProfileLevel.HEVCHighTierLevel62 to 186,
            )
    }
}

private fun Int?.toAudioOutput(): AudioOutput = when (this) {
    AudioDeviceInfo.TYPE_HDMI -> AudioOutput.Hdmi

    AudioDeviceInfo.TYPE_HDMI_ARC -> AudioOutput.HdmiArc

    AudioDeviceInfo.TYPE_HDMI_EARC -> AudioOutput.HdmiEarc

    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    -> AudioOutput.Bluetooth

    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutput.Speaker

    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    -> AudioOutput.Usb

    else -> AudioOutput.Unknown
}

private fun List<MediaCodecInfo>.profilesFor(mime: String): Set<Int> =
    filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
        .flatMap { info -> info.getCapabilitiesForType(mime).profileLevels.map { it.profile } }
        .toSet()

private fun List<MediaCodecInfo>.maxLevel(mime: String, levels: Map<Int, Int>): Int? =
    filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
        .flatMap { info ->
            info.getCapabilitiesForType(mime).profileLevels.mapNotNull { levels[it.level] }
        }
        .maxOrNull()

private fun List<MediaCodecInfo>.maxSize(mime: String): Pair<Int, Int>? =
    filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
        .mapNotNull { info -> info.getCapabilitiesForType(mime).videoCapabilities }
        .maxByOrNull { it.supportedWidths.upper * it.supportedHeights.upper }
        ?.let { it.supportedWidths.upper to it.supportedHeights.upper }

private const val PROBE_SAMPLE_RATE_HZ = 48_000
private const val FALLBACK_MAX_WIDTH = 1920
private const val FALLBACK_MAX_HEIGHT = 1080

private data class DecoderCapabilities(
    val videoCodecs: Set<String>,
    val hevcMain10: Boolean,
    val avcHigh10: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val hdrTypes: Set<HdrType>,
    val dolbyVisionProfiles: Set<Int>,
    val decodableAudio: Set<String>,
    val maxLevels: Map<String, Int>,
    val maxSizes: Map<String, VideoSize>,
    val vp9Profile2: Boolean,
    val av1Main10: Boolean,
)
