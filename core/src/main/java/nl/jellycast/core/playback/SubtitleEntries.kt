package nl.jellycast.core.playback

import java.text.Collator
import java.util.Locale

/** A subtitle track as the menu shows it: the language, and what tells it apart from the others. */
data class SubtitleEntry(
    val option: TrackOption,
    /** In the app's language; `null` when the file does not say. */
    val language: String?,
    /** A name from the file that adds something to the language, e.g. "Simplified". */
    val title: String?,
    val isSdh: Boolean,
    val isForced: Boolean,
    /** Drawn as images (PGS, VobSub): the size and background settings do not apply. */
    val isImage: Boolean,
    /** 2, 3, … for tracks that would otherwise look the same; `null` for the first. */
    val number: Int?,
)

/**
 * The [preferred] languages first, in that order, then the rest alphabetically by name. Within a
 * language the plain track comes before SDH, and forced comes last.
 */
fun subtitleEntries(
    options: List<TrackOption>,
    preferred: List<String>,
    locale: Locale,
): List<SubtitleEntry> {
    val preferredCodes = preferred.mapNotNull(::normalizeLanguage)
    val titles = options.map { it.label?.trim() }
    val sharedTitle = titles.distinct().singleOrNull().takeIf { options.size > 1 }
    val collator = Collator.getInstance(locale).let {
        Comparator<String> { a, b -> it.compare(a, b) }
    }

    val ranked =
        options.map { option ->
            val code = normalizeLanguage(option.language)
            val title = option.label?.trim()?.takeUnless { it == sharedTitle }
            val keywords = title?.lowercase(Locale.ROOT).orEmpty()
            val entry =
                SubtitleEntry(
                    option = option,
                    language = code?.let { languageName(it, locale) },
                    title = title?.let { meaningfulTitle(it, code, locale) },
                    isSdh = option.isSdh || SDH_WORDS.any { containsWord(keywords, it) },
                    isForced = option.isForced || containsWord(keywords, "forced"),
                    isImage = option.codec in IMAGE_CODECS,
                    number = null,
                )
            val preference = preferredCodes.indexOf(code).takeIf { it >= 0 }
            entry to (preference ?: preferredCodes.size)
        }

    val sorted =
        ranked
            .sortedWith(
                compareBy<Pair<SubtitleEntry, Int>> { it.second }
                    .thenBy { it.first.language == null }
                    .thenBy(collator) { it.first.language ?: it.first.title.orEmpty() }
                    .thenBy { it.first.variantOrder() },
            ).map { it.first }

    val seen = mutableMapOf<List<Any?>, Int>()
    return sorted.map { entry ->
        val key = listOf(entry.language, entry.title, entry.isSdh, entry.isForced, entry.isImage)
        val count = (seen[key] ?: 0) + 1
        seen[key] = count
        entry.copy(number = count.takeIf { it > 1 })
    }
}

private fun SubtitleEntry.variantOrder(): Int = when {
    isForced -> 2
    isSdh -> 1
    else -> 0
}

/** "Nederlands" for "nl" when [locale] is Dutch; the code itself when Java does not know it. */
fun languageName(code: String, locale: Locale): String = Locale
    .forLanguageTag(TWO_LETTER_CODES[code] ?: code)
    .getDisplayLanguage(locale)
    .replaceFirstChar { it.titlecase(locale) }
    .ifBlank { code }

/**
 * The file's name for a track, unless it only repeats what the menu already shows: the language
 * (in any of the usual spellings), SDH, forced or the format. Long names are usually release names.
 */
private fun meaningfulTitle(title: String, code: String?, locale: Locale): String? {
    if (title.length > MAX_TITLE_LENGTH) return null
    var rest = title.lowercase(Locale.ROOT)
    val names =
        code?.let { language ->
            val tag = Locale.forLanguageTag(TWO_LETTER_CODES[language] ?: language)
            listOf(locale, Locale.ENGLISH, tag).map { tag.getDisplayLanguage(it).lowercase(it) }
        }.orEmpty()
    (names + NOISE_WORDS).sortedByDescending { it.length }.forEach { word ->
        rest = rest.replace(Regex("(?<!\\p{L})${Regex.escape(word)}(?!\\p{L})"), " ")
    }
    return title.takeIf { rest.any { it.isLetterOrDigit() } }
}

private fun containsWord(text: String, word: String): Boolean =
    Regex("(?<!\\p{L})${Regex.escape(word)}(?!\\p{L})").containsMatchIn(text)

/** Java names "nl" but not "nld", and [normalizeLanguage] gives the three-letter form. */
private val TWO_LETTER_CODES: Map<String, String> =
    Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).isO3Language }

private const val MAX_TITLE_LENGTH = 40
private val IMAGE_CODECS = setOf("PGS", "VobSub")
private val SDH_WORDS = listOf("sdh", "cc", "hearing impaired")
private val NOISE_WORDS =
    SDH_WORDS +
        listOf("forced", "full", "default", "subtitles", "subtitle", "subs") +
        listOf("srt", "subrip", "ass", "ssa", "pgs", "vobsub")
