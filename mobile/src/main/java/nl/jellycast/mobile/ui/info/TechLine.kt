package nl.jellycast.mobile.ui.info

import nl.jellycast.core.model.MediaInfo
import nl.jellycast.core.model.MediaStreamInfo
import nl.jellycast.core.model.StreamKind

/** "4K", "Dolby Vision", "TrueHD Atmos", "PGS" from the default tracks of the file. */
fun techBadges(info: MediaInfo): List<String> {
    val video = info.streams.firstOrNull { it.kind == StreamKind.Video }
    val audio =
        info.streams.firstOrNull { it.kind == StreamKind.Audio && it.isDefault }
            ?: info.streams.firstOrNull { it.kind == StreamKind.Audio }
    val hasPgs =
        info.streams.any { it.kind == StreamKind.Subtitle && it.codec.equals("pgssub", true) }
    return listOfNotNull(
        video?.let(::resolutionName),
        video?.let(::rangeName),
        audio?.let(::audioName),
        if (hasPgs) "PGS" else null,
    )
}

private fun resolutionName(stream: MediaStreamInfo): String? {
    val height = stream.height ?: return null
    return when {
        height > UHD_MIN_HEIGHT -> "4K"
        height > FULL_HD_MIN_HEIGHT -> "1080p"
        height > 0 -> "${height}p"
        else -> null
    }
}

private fun rangeName(stream: MediaStreamInfo): String? {
    val type = stream.videoRangeType.orEmpty()
    return when {
        type.startsWith("DOVI") -> "Dolby Vision"
        type == "HDR10Plus" -> "HDR10+"
        type == "HDR10" -> "HDR10"
        type == "HLG" -> "HLG"
        else -> null
    }
}

private fun audioName(stream: MediaStreamInfo): String? {
    val profile = stream.profile.orEmpty()
    val atmos = "Atmos" in profile
    return when (stream.codec?.lowercase()) {
        "truehd" -> if (atmos) "TrueHD Atmos" else "TrueHD"
        "eac3" -> if (atmos) "Dolby Atmos" else "Dolby Digital+"
        "ac3" -> "Dolby Digital"
        "dts" ->
            when {
                "MA" in profile -> "DTS-HD MA"
                ":X" in profile -> "DTS:X"
                else -> "DTS"
            }
        "aac" -> stream.channels?.takeIf { it > 2 }?.let { "AAC ${it}ch" }
        "flac" -> "FLAC"
        else -> null
    }
}

private const val UHD_MIN_HEIGHT = 1100
private const val FULL_HD_MIN_HEIGHT = 730
