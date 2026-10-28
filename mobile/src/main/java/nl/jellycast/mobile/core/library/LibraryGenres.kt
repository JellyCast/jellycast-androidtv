package nl.jellycast.mobile.core.library

/**
 * The server's genres come from TMDb in English, partly as combined names; split so one genre is
 * one filter entry, as iOS `Genres`. Display names come from `genreStringRes` in `:core`.
 */
object LibraryGenres {
    private val combined =
        mapOf(
            "Sci-Fi & Fantasy" to listOf("Science Fiction", "Fantasy"),
            "Action & Adventure" to listOf("Action", "Adventure"),
            "War & Politics" to listOf("War", "Politics"),
            "Sci-Fi" to listOf("Science Fiction"),
            "Kids" to listOf("Family"),
        )

    /** In the server's order, without duplicates. */
    fun canonical(genres: List<String>): List<String> =
        genres.flatMap { combined[it] ?: listOf(it) }.distinct()
}

/** The decade a year falls in ("2010" for 2014), for the Library's Year sections. */
fun decadeOf(year: Int?): Int? = year?.let { it - it.mod(DECADE) }

private const val DECADE = 10
