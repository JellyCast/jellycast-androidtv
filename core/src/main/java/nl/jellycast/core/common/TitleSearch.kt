package nl.jellycast.core.common

import java.text.Normalizer

/** "godf" finds "The Godfather" and "amelie" finds "Amélie". An empty query matches all. */
fun matchesIgnoringCaseAndAccents(title: String, query: String): Boolean {
    val needle = query.trim()
    return needle.isEmpty() || normalize(title).contains(normalize(needle))
}

private val Diacritics = Regex("\\p{Mn}+")

private fun normalize(text: String): String =
    Diacritics.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
