package nl.jellycast.tv.core.playback

import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.deviceprofile.DeviceProfileBuilder
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile

private val DirectPlayContainers =
    arrayOf("mkv", "webm", "mp4", "m4v", "mov", "ts", "m2ts", "mpegts")

/** Media3 renders these itself, so they are embedded and never burned in by the server. */
private val EmbeddedSubtitleFormats =
    listOf(
        "srt", "subrip", "ass", "ssa", "pgs", "pgssub", "dvdsub", "vobsub",
        "mov_text", "tx3g", "vtt", "webvtt", "ttml", "dvbsub",
    )

private val AssFormats = setOf("ass", "ssa")

/** Codecs that come out of every decoder as stereo PCM; the server transcodes the rest. */
private val StereoAudioCodecs = setOf("aac", "mp3", "opus", "vorbis", "flac")

/** EAC3 from the server goes no higher than 5.1. */
private const val MAX_TRANSCODE_CHANNELS = 6
private const val STEREO_CHANNELS = 2
private const val EIGHT_BIT = 8

/** FFmpeg decodes stereo TrueHD badly (androidx/media#1843), so only surround plays locally. */
private const val MIN_LOCAL_TRUEHD_CHANNELS = 3

fun createDeviceProfile(
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings = PlaybackSettings(),
): DeviceProfile {
    val directPlayAudio = directPlayAudioCodecs(capabilities, settings)

    return buildDeviceProfile {
        name = "JellyCast"
        maxStreamingBitrate = settings.maxStreamingBitrate
        maxStaticBitrate = settings.maxStreamingBitrate

        directPlayProfile {
            type = DlnaProfileType.VIDEO
            container(*DirectPlayContainers)
            videoCodec(*capabilities.videoCodecs.sorted().toTypedArray())
            audioCodec(*directPlayAudio.sorted().toTypedArray())
        }
        hlsTranscodingProfile(capabilities, settings)
        hevcCodecProfile(capabilities)
        h264CodecProfile(capabilities)
        tenBitCodecProfiles(capabilities)
        audioCodecProfiles(capabilities, settings, directPlayAudio)
        subtitleProfiles(settings)
    }
}

/** Video is copied (remux) where possible, so the server only touches audio or container. */
private fun DeviceProfileBuilder.hlsTranscodingProfile(
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings,
) {
    transcodingProfile {
        type = DlnaProfileType.VIDEO
        context = EncodingContext.STREAMING
        protocol = MediaStreamProtocol.HLS
        container = "ts"
        videoCodec(
            *listOf("hevc", "h264").filter {
                it in capabilities.videoCodecs
            }.toTypedArray(),
        )
        audioCodec(*transcodeAudioCodecs(capabilities, settings).toTypedArray())
        maxAudioChannels = transcodeAudioChannels(capabilities, settings).toString()
        copyTimestamps = false
        enableSubtitlesInManifest = true
    }
}

private fun DeviceProfileBuilder.hevcCodecProfile(capabilities: DeviceCapabilities) {
    codecProfile {
        type = CodecType.VIDEO
        codec = "hevc"
        conditions {
            ProfileConditionValue.WIDTH lowerThanOrEquals capabilities.maxVideoWidth
            ProfileConditionValue.HEIGHT lowerThanOrEquals capabilities.maxVideoHeight
            capabilities.maxVideoLevels["hevc"]?.let {
                ProfileConditionValue.VIDEO_LEVEL lowerThanOrEquals it
            }
            if (!capabilities.hevcMain10) {
                ProfileConditionValue.VIDEO_BIT_DEPTH lowerThanOrEquals EIGHT_BIT
            }
            ProfileConditionValue.VIDEO_RANGE_TYPE inCollection
                hevcRangeTypes(capabilities).map { it.serialName }
        }
    }
}

private fun DeviceProfileBuilder.h264CodecProfile(capabilities: DeviceCapabilities) {
    codecProfile {
        type = CodecType.VIDEO
        codec = "h264"
        conditions {
            capabilities.maxVideoSizes["h264"]?.let {
                ProfileConditionValue.WIDTH lowerThanOrEquals it.width
                ProfileConditionValue.HEIGHT lowerThanOrEquals it.height
            }
            capabilities.maxVideoLevels["h264"]?.let {
                ProfileConditionValue.VIDEO_LEVEL lowerThanOrEquals it
            }
            if (!capabilities.avcHigh10) {
                ProfileConditionValue.VIDEO_BIT_DEPTH lowerThanOrEquals EIGHT_BIT
            }
            ProfileConditionValue.VIDEO_RANGE_TYPE inCollection
                listOf(VideoRangeType.SDR.serialName)
        }
    }
}

/** VP9 and AV1: 10-bit and HDR only with a 10-bit decoder and an HDR display. */
private fun DeviceProfileBuilder.tenBitCodecProfiles(capabilities: DeviceCapabilities) {
    for ((codec, tenBit) in listOf(
        "vp9" to capabilities.vp9Profile2,
        "av1" to capabilities.av1Main10,
    )) {
        if (codec !in capabilities.videoCodecs) continue
        codecProfile {
            type = CodecType.VIDEO
            this.codec = codec
            conditions {
                capabilities.maxVideoSizes[codec]?.let {
                    ProfileConditionValue.WIDTH lowerThanOrEquals it.width
                    ProfileConditionValue.HEIGHT lowerThanOrEquals it.height
                }
                if (!tenBit) ProfileConditionValue.VIDEO_BIT_DEPTH lowerThanOrEquals EIGHT_BIT
                ProfileConditionValue.VIDEO_RANGE_TYPE inCollection
                    tenBitRangeTypes(capabilities, tenBit).map { it.serialName }
            }
        }
    }
}

private fun DeviceProfileBuilder.audioCodecProfiles(
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings,
    directPlayAudio: Set<String>,
) {
    if (settings.downmixToStereo) {
        codecProfile {
            type = CodecType.VIDEO_AUDIO
            conditions { ProfileConditionValue.AUDIO_CHANNELS lowerThanOrEquals STEREO_CHANNELS }
        }
    }
    if ("truehd" in directPlayAudio && "truehd" !in capabilities.passthroughAudio) {
        codecProfile {
            type = CodecType.VIDEO_AUDIO
            codec = "truehd"
            conditions {
                ProfileConditionValue.AUDIO_CHANNELS greaterThanOrEquals MIN_LOCAL_TRUEHD_CHANNELS
            }
        }
    }
}

private fun DeviceProfileBuilder.subtitleProfiles(settings: PlaybackSettings) {
    for (format in EmbeddedSubtitleFormats) {
        if (settings.burnInAss && format in AssFormats) {
            subtitleProfile(format, SubtitleDeliveryMethod.ENCODE)
        } else {
            subtitleProfile(format, SubtitleDeliveryMethod.EMBED)
        }
    }
    subtitleProfile("vtt", SubtitleDeliveryMethod.HLS)
    // No `External`: the server would then extract every text subtitle on each start, reading the
    // whole movie from disk.
}

/**
 * Passthrough plus what the device decodes. TrueHD/DTS only when decoded locally; otherwise the
 * server transcodes just the audio and leaves the video untouched.
 */
internal fun directPlayAudioCodecs(
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings,
): Set<String> {
    if (settings.downmixToStereo) return capabilities.decodableAudio intersect StereoAudioCodecs
    val decodable =
        if (decodesLosslessLocally(capabilities, settings)) {
            capabilities.decodableAudio
        } else {
            capabilities.decodableAudio - LosslessAudioCodecs
        }
    return capabilities.passthroughAudio + decodable
}

/** Prefer something that can pass through to the amplifier (EAC3/AC3), otherwise AAC. */
private fun transcodeAudioCodecs(
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings,
): List<String> {
    if (settings.downmixToStereo) return listOf("aac")
    return listOf("eac3", "ac3").filter {
        it in capabilities.passthroughAudio ||
            it in capabilities.decodableAudio
    } +
        "aac"
}

/**
 * 5.1 when EAC3/AC3 passes through to an amplifier. Otherwise the device decodes, so a stereo
 * output gets stereo right away instead of 5.1 that is downmixed locally.
 */
internal fun transcodeAudioChannels(
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings,
): Int = when {
    settings.downmixToStereo -> STEREO_CHANNELS
    capabilities.passthroughAudio.any { it == "eac3" || it == "ac3" } -> MAX_TRANSCODE_CHANNELS
    else -> capabilities.maxPcmChannels.coerceIn(STEREO_CHANNELS, MAX_TRANSCODE_CHANNELS)
}

/**
 * DV profile 7 (`DOVIWithEL`) and 8.6 (`DOVIInvalid`) are declared whenever HDR10 works: the
 * player plays the HDR10 base layer (p7) or uses the DV decoder (8.6), so the server does nothing.
 * HDR10+ without an HDR10+ display plays as HDR10.
 */
internal fun hevcRangeTypes(capabilities: DeviceCapabilities): Set<VideoRangeType> {
    val hdr = capabilities.hdrTypes
    val dv = HdrType.DolbyVision in hdr
    val dvProfile8 = dv && 8 in capabilities.dolbyVisionProfiles
    val dvProfile5 = dv && 5 in capabilities.dolbyVisionProfiles
    val hdr10 = HdrType.Hdr10 in hdr

    return buildSet {
        add(VideoRangeType.SDR)
        if (HdrType.Hlg in hdr) add(VideoRangeType.HLG)
        if (hdr10) {
            add(VideoRangeType.HDR10)
            add(VideoRangeType.HDR10_PLUS)
            add(VideoRangeType.DOVI_WITH_HDR10)
            add(VideoRangeType.DOVI_WITH_EL)
            add(VideoRangeType.DOVI_INVALID)
            if (!capabilities.dolbyVisionHdr10PlusBroken) {
                add(VideoRangeType.DOVI_WITH_HDR10_PLUS)
                add(VideoRangeType.DOVI_WITH_ELHDR10_PLUS)
            }
        }
        if (dvProfile8 || HdrType.Hlg in hdr) add(VideoRangeType.DOVI_WITH_HLG)
        if (dvProfile8) add(VideoRangeType.DOVI_WITH_SDR)
        // Profile 5 has no fallback layer: without a DV decoder it turns green and purple.
        if (dvProfile5) add(VideoRangeType.DOVI)
    }
}

internal fun tenBitRangeTypes(
    capabilities: DeviceCapabilities,
    tenBit: Boolean,
): Set<VideoRangeType> = buildSet {
    add(VideoRangeType.SDR)
    if (!tenBit) return@buildSet
    if (HdrType.Hdr10 in capabilities.hdrTypes) {
        add(VideoRangeType.HDR10)
        add(VideoRangeType.HDR10_PLUS)
    }
    if (HdrType.Hlg in capabilities.hdrTypes) add(VideoRangeType.HLG)
}
