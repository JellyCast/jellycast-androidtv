package nl.jellycast.tv.feature.settings

import nl.jellycast.core.playback.DeviceCapabilities
import nl.jellycast.core.playback.HdrType

/** Jellyfin codec name → display name, in display order. */
private val VideoNames =
    linkedMapOf(
        "hevc" to "HEVC",
        "h264" to "H.264",
        "vp9" to "VP9",
        "av1" to "AV1",
        "mpeg2video" to "MPEG-2",
        "mpeg4" to "MPEG-4",
        "vc1" to "VC-1",
    )

/** Leaves out `dca` and `mlp`, aliases of DTS and TrueHD, so they are not listed twice. */
private val PassthroughNames =
    linkedMapOf(
        "ac3" to "AC3",
        "eac3" to "EAC3",
        "dts" to "DTS",
        "truehd" to "TrueHD",
    )

private const val UHD_WIDTH = 3840
private const val FULL_HD_WIDTH = 1920

internal fun DeviceCapabilities.toDeviceInfo(): DeviceInfo = DeviceInfo(
    video =
        VideoNames.mapNotNull { (codec, name) ->
            when {
                codec !in videoCodecs -> null
                codec == "hevc" && hevcMain10 -> "$name Main10"
                codec == "h264" && avcHigh10 -> "$name High10"
                else -> name
            }
        },
    // Width only: decoders also report the portrait height (4096×4096).
    maxResolution =
        when {
            maxVideoWidth >= UHD_WIDTH -> "4K"
            maxVideoWidth >= FULL_HD_WIDTH -> "1080p"
            else -> "${maxVideoWidth}px"
        },
    hdr =
        listOfNotNull(
            if (HdrType.DolbyVision in hdrTypes) {
                "Dolby Vision " +
                    dolbyVisionProfiles.sorted().joinToString(prefix = "(", postfix = ")")
            } else {
                null
            },
            "HDR10".takeIf { HdrType.Hdr10 in hdrTypes },
            "HDR10+".takeIf { HdrType.Hdr10Plus in hdrTypes },
            "HLG".takeIf { HdrType.Hlg in hdrTypes },
        ),
    passthrough = PassthroughNames.filterKeys { it in passthroughAudio }.values.toList(),
    maxPcmChannels = maxPcmChannels,
    audioOutput = audioOutput,
)
