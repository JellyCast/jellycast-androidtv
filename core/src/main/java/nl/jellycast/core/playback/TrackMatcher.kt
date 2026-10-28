package nl.jellycast.core.playback

import java.util.Locale

/** A track as ExoPlayer sees it in the file, free of Media3 types for JVM tests. */
data class PlayerTrack(
    val label: String?,
    val mimeType: String?,
    val channels: Int?,
    val language: String?,
)

data class ServerTrack(
    val title: String?,
    val codec: String,
    val channels: Int?,
    val language: String?,
)

private val CodecMimeTypes =
    mapOf(
        "ac3" to setOf("audio/ac3"),
        "eac3" to setOf("audio/eac3", "audio/eac3-joc"),
        "truehd" to setOf("audio/true-hd"),
        "mlp" to setOf("audio/true-hd"),
        "dts" to setOf("audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.uhd"),
        "dca" to setOf("audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.uhd"),
        "aac" to setOf("audio/mp4a-latm"),
        "mp3" to setOf("audio/mpeg"),
        "flac" to setOf("audio/flac"),
        "opus" to setOf("audio/opus"),
        "vorbis" to setOf("audio/vorbis"),
        "subrip" to setOf("application/x-subrip"),
        "srt" to setOf("application/x-subrip"),
        "ass" to setOf("text/x-ssa"),
        "ssa" to setOf("text/x-ssa"),
        "pgssub" to setOf("application/pgs"),
        "dvdsub" to setOf("application/vobsub"),
        "mov_text" to setOf("application/x-quicktime-tx3g"),
    )

/**
 * Position alone is unreliable (it once picked the commentary track). Matches by exact title,
 * then by codec, channels and language, and only then by the server's position.
 */
fun matchTrack(target: ServerTrack, targetPosition: Int, candidates: List<PlayerTrack>): Int? {
    if (candidates.isEmpty()) return null
    target.title?.takeIf { it.isNotBlank() }?.let { title ->
        val byLabel = candidates.indices.filter { candidates[it].label == title }
        if (byLabel.size == 1) return byLabel.single()
    }
    val mimeTypes = CodecMimeTypes[target.codec.lowercase()].orEmpty()
    val targetLanguage = target.language.iso3()
    candidates.indices
        .firstOrNull { i ->
            val c = candidates[i]
            c.mimeType in mimeTypes &&
                (target.channels == null || c.channels == null || c.channels == target.channels) &&
                (targetLanguage == null || c.language.iso3() == targetLanguage)
        }?.let { return it }
    return targetPosition.takeIf { it in candidates.indices }
}

/**
 * Subtitle tracks often share codec and language (plain and SDH, two Spanish), so the file order
 * decides before codec and language do; the server lists the file's tracks in the same order.
 */
fun matchSubtitle(target: ServerTrack, targetPosition: Int, candidates: List<PlayerTrack>): Int? {
    target.title?.takeIf { it.isNotBlank() }?.let { title ->
        val byLabel = candidates.indices.filter { candidates[it].label == title }
        if (byLabel.size == 1) return byLabel.single()
    }
    val atPosition = candidates.getOrNull(targetPosition)
    val mimeTypes = CodecMimeTypes[target.codec.lowercase()].orEmpty()
    if (atPosition != null &&
        (mimeTypes.isEmpty() || atPosition.mimeType in mimeTypes) &&
        (target.language.iso3() == null || atPosition.language.iso3() == target.language.iso3())
    ) {
        return targetPosition
    }
    return matchTrack(target, targetPosition, candidates)
}

/** Makes `en` and `eng` compare equal. */
private fun String?.iso3(): String? = this?.takeIf { it.isNotBlank() && it != "und" }?.let {
    try {
        Locale.forLanguageTag(it).isO3Language.ifEmpty { it }
    } catch (_: java.util.MissingResourceException) {
        it
    }
}
