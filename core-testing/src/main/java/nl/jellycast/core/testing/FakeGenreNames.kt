package nl.jellycast.core.testing

import java.text.Collator
import java.util.Locale
import nl.jellycast.core.model.GenreNames

/** Defaults to a few Dutch genre names. */
class FakeGenreNames(
    private val names: Map<String, String> =
        mapOf(
            "Action" to "Actie",
            "Crime" to "Misdaad",
            "Science Fiction" to "Sciencefiction",
        ),
    private val locale: Locale = Locale.forLanguageTag("nl"),
) : GenreNames {
    override fun translate(serverName: String): String = names[serverName] ?: serverName

    override fun order(): Comparator<String> {
        val collator = Collator.getInstance(locale)
        return Comparator { a, b -> collator.compare(a, b) }
    }
}
