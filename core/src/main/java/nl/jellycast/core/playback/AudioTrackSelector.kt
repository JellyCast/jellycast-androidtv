package nl.jellycast.core.playback

import java.util.Locale
import java.util.MissingResourceException
import nl.jellycast.core.model.AudioTrack

/** Replacements for TrueHD/DTS, in order of preference (EAC3 can carry Atmos). */
private val AlternativePreference = listOf("eac3", "ac3")

private val PcmCodecs = setOf("pcm_s16le", "pcm_s24le", "pcm_s32le")

/** Tried after the title's original language: dubbed releases often mark their dub as default. */
val FallbackAudioLanguages = listOf("eng", "nld")

/**
 * Starts from a track in the first of [preferredLanguages] the file has (the default track when it
 * is in that language), else from the default track. A TrueHD/DTS track that
 * cannot pass through is replaced by an EAC3/AC3 track in the same language, which costs the
 * server nothing; then by a PCM track with at least as many channels (a stereo PCM track next to
 * a 5.1 main track would lose surround). When the device decodes lossless audio itself, only an
 * EAC3 passthrough track (Atmos) replaces it.
 */
fun selectAudioTrack(
    tracks: List<AudioTrack>,
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings,
    preferredLanguages: List<String> = FallbackAudioLanguages,
): AudioTrack? {
    if (tracks.isEmpty()) return null
    val base =
        preferredLanguages.firstNotNullOfOrNull { tracks.mainTrackIn(it) }
            ?: tracks.firstOrNull { it.isDefault }
            ?: tracks.first()

    val codec = base.codec.lowercase()
    if (codec !in LosslessAudioCodecs || codec in capabilities.passthroughAudio) return base

    val sameLanguage =
        tracks.filter { sameLanguage(it.language, base.language) && !it.isCommentary() }
    if (decodesLosslessLocally(capabilities, settings)) {
        // Local decoding loses the Atmos height channels that EAC3-JOC passthrough keeps.
        return sameLanguage
            .filter {
                it.codec.equals("eac3", ignoreCase = true) &&
                    "eac3" in capabilities.passthroughAudio
            }
            .maxByOrNull { it.channels ?: 0 } ?: base
    }
    val lossy =
        AlternativePreference
            .filter { it in capabilities.passthroughAudio || it in capabilities.decodableAudio }
            .firstNotNullOfOrNull { preferred ->
                sameLanguage
                    .filter { it.codec.equals(preferred, ignoreCase = true) }
                    .maxByOrNull { it.channels ?: 0 }
            }
    if (lossy != null) return lossy

    val baseChannels = base.channels ?: return base
    return sameLanguage
        .filter {
            val pcm = it.codec.lowercase()
            pcm in PcmCodecs && pcm in capabilities.decodableAudio &&
                (it.channels ?: 0) >= baseChannels
        }.maxByOrNull { it.channels ?: 0 } ?: base
}

private fun AudioTrack.isCommentary(): Boolean =
    title?.contains("comment", ignoreCase = true) == true

/** The track a played track stands in for: the main track in its language. */
fun List<AudioTrack>.sourceOf(played: AudioTrack): AudioTrack =
    mainTrackIn(played.language) ?: played

private fun List<AudioTrack>.mainTrackIn(language: String?): AudioTrack? {
    val candidates = filter { sameLanguage(it.language, language) && !it.isCommentary() }
    return candidates.firstOrNull { it.isDefault } ?: candidates.firstOrNull()
}

/**
 * Track tags mix ISO 639-2/B ("fre", "dut") and /T ("fra", "nld"); Wikidata gives ISO 639-1
 * ("fr"). Everything becomes 639-2/T.
 */
fun normalizeLanguage(code: String?): String? {
    val lower = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" } ?: return null
    BibliographicCodes[lower]?.let { return it }
    if (lower.length != 2) return lower
    return try {
        Locale.forLanguageTag(lower).isO3Language.ifEmpty { lower }
    } catch (_: MissingResourceException) {
        lower
    }
}

private fun sameLanguage(a: String?, b: String?): Boolean {
    val first = normalizeLanguage(a) ?: return false
    return first == normalizeLanguage(b)
}

private val BibliographicCodes =
    mapOf(
        "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho",
        "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu",
        "gre" to "ell", "ice" to "isl", "mac" to "mkd", "may" to "msa", "per" to "fas",
        "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
    )
