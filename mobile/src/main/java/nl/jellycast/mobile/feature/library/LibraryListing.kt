package nl.jellycast.mobile.feature.library

import java.text.Collator
import nl.jellycast.core.common.matchesIgnoringCaseAndAccents
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.progress
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.SeriesStatus

enum class LibraryKind { All, Series, Movies }

enum class LibrarySort { Newest, Title, Year, Length }

enum class WatchStatus { NotStarted, InProgress, Watched }

data class LibraryFilters(
    val kind: LibraryKind = LibraryKind.All,
    val genre: String? = null,
    val status: WatchStatus? = WatchStatus.NotStarted,
    val sort: LibrarySort = LibrarySort.Newest,
) {
    val isNarrowed: Boolean get() = kind != LibraryKind.All || genre != null || status != null
}

/** One tile: a movie or a series, with what the Library sorts and filters on. */
sealed interface LibraryEntry {
    val id: String
    val title: String
    val year: Int?
    val addedAt: Long?
    val genres: List<String>
    val posterUrl: String?
    val status: WatchStatus
    val runtimeMs: Long

    /** Only a movie in progress shows a bar. */
    val progress: Float?

    data class MovieEntry(val movie: Movie, override val status: WatchStatus) : LibraryEntry {
        override val id get() = movie.id
        override val title get() = movie.title
        override val year get() = movie.year
        override val addedAt get() = movie.addedAt
        override val genres get() = movie.genres
        override val posterUrl get() = movie.artwork.poster
        override val runtimeMs get() = movie.runtimeMs ?: 0
        override val progress: Float?
            get() = if (status == WatchStatus.InProgress) movie.progress else null
    }

    data class SeriesEntry(
        val series: Series,
        override val status: WatchStatus,
        override val runtimeMs: Long,
    ) : LibraryEntry {
        override val id get() = series.id
        override val title get() = series.title
        override val year get() = series.year
        override val addedAt get() = series.lastEpisodeAddedAt
        override val genres get() = series.genres
        override val posterUrl get() = series.artwork.poster
        override val progress: Float? get() = null
    }
}

/** What a section is called; the screen turns it into text. */
sealed interface SectionKey {
    data object AddedThisWeek : SectionKey

    data object AddedThisMonth : SectionKey

    data object Earlier : SectionKey

    data class Letter(val letter: String) : SectionKey

    data class Decade(val decade: Int) : SectionKey

    data object UnknownYear : SectionKey

    data object OverThreeHours : SectionKey

    data object TwoToThreeHours : SectionKey

    data object UnderTwoHours : SectionKey
}

data class LibrarySection(val key: SectionKey, val entries: List<LibraryEntry>)

data class GenreCount(val genre: String, val count: Int)

object LibraryListing {
    fun entries(index: LibraryIndex, kind: LibraryKind): List<LibraryEntry> {
        val movies =
            if (kind == LibraryKind.Series) {
                emptyList()
            } else {
                index.snapshot.movies.map { LibraryEntry.MovieEntry(it, statusOf(it)) }
            }
        val series =
            if (kind == LibraryKind.Movies) {
                emptyList()
            } else {
                index.snapshot.series.map { series ->
                    val progress = index.progress(series.id)
                    LibraryEntry.SeriesEntry(
                        series = series,
                        status = progress?.status?.toWatchStatus() ?: statusOf(series),
                        runtimeMs = progress?.episodes?.sumOf { it.runtimeMs ?: 0 } ?: 0,
                    )
                }
            }
        return movies + series
    }

    fun filtered(entries: List<LibraryEntry>, genre: String?, query: String): List<LibraryEntry> =
        entries.filter { entry ->
            (genre == null || genre in canonicalGenres(entry.genres)) &&
                matchesIgnoringCaseAndAccents(entry.title, query)
        }

    /** Most titles first. */
    fun genreCounts(entries: List<LibraryEntry>): List<GenreCount> = entries
        .flatMap { canonicalGenres(it.genres) }
        .groupingBy { it }
        .eachCount()
        .map { GenreCount(it.key, it.value) }
        .sortedWith(compareByDescending<GenreCount> { it.count }.thenBy { it.genre })

    fun sections(
        entries: List<LibraryEntry>,
        status: WatchStatus?,
        sort: LibrarySort,
        nowMs: Long,
        collator: Collator = Collator.getInstance(),
    ): List<LibrarySection> {
        val shown = if (status == null) entries else entries.filter { it.status == status }
        return when (sort) {
            LibrarySort.Newest ->
                shown
                    .sortedByDescending { it.addedAt ?: Long.MIN_VALUE }
                    .groupInOrder { addedSection(it.addedAt, nowMs) }
            LibrarySort.Title ->
                shown
                    .sortedWith { a, b -> collator.compare(sortKey(a.title), sortKey(b.title)) }
                    .groupInOrder { letterSection(it.title) }
            LibrarySort.Year ->
                shown
                    .sortedByDescending { it.year ?: 0 }
                    .groupInOrder { entry ->
                        entry.year?.let { SectionKey.Decade(it / DECADE * DECADE) }
                            ?: SectionKey.UnknownYear
                    }
            LibrarySort.Length ->
                shown
                    .sortedByDescending { it.runtimeMs }
                    .groupInOrder { lengthSection(it.runtimeMs) }
        }
    }

    /** "Sci-Fi & Fantasy" counts as both Science Fiction and Fantasy. */
    fun canonicalGenres(genres: List<String>): Set<String> =
        genres.flatMap { CombinedGenres[it] ?: listOf(it) }.toSet()

    private fun addedSection(addedAt: Long?, nowMs: Long): SectionKey {
        val days = addedAt?.let { (nowMs - it) / DAY_MS } ?: Long.MAX_VALUE
        return when {
            days < WEEK_DAYS -> SectionKey.AddedThisWeek
            days < MONTH_DAYS -> SectionKey.AddedThisMonth
            else -> SectionKey.Earlier
        }
    }

    private fun letterSection(title: String): SectionKey {
        val first = sortKey(title).firstOrNull()
        return if (first != null && first.isLetter()) {
            SectionKey.Letter(first.uppercase())
        } else {
            SectionKey.Letter(NON_LETTER)
        }
    }

    private fun lengthSection(runtimeMs: Long): SectionKey = when {
        runtimeMs > LONG_MS -> SectionKey.OverThreeHours
        runtimeMs > SHORT_MS -> SectionKey.TwoToThreeHours
        else -> SectionKey.UnderTwoHours
    }

    private fun sortKey(title: String): String {
        val article = Articles.firstOrNull { title.startsWith(it) } ?: return title
        return title.removePrefix(article)
    }

    private fun List<LibraryEntry>.groupInOrder(
        key: (LibraryEntry) -> SectionKey,
    ): List<LibrarySection> {
        val sections = mutableListOf<LibrarySection>()
        for (entry in this) {
            val sectionKey = key(entry)
            val last = sections.lastOrNull()
            if (last != null && last.key == sectionKey) {
                sections[sections.lastIndex] = last.copy(entries = last.entries + entry)
            } else {
                sections += LibrarySection(sectionKey, listOf(entry))
            }
        }
        return sections
    }
}

fun statusOf(movie: Movie): WatchStatus = when {
    movie.isPlayed -> WatchStatus.Watched
    movie.resumePositionMs > 0 -> WatchStatus.InProgress
    else -> WatchStatus.NotStarted
}

/** Without episodes in the snapshot: the server's own flags. */
private fun statusOf(series: Series): WatchStatus = when {
    series.isPlayed -> WatchStatus.Watched
    series.hasPlayedEpisodes -> WatchStatus.InProgress
    else -> WatchStatus.NotStarted
}

private fun SeriesStatus.toWatchStatus(): WatchStatus = when (this) {
    SeriesStatus.NotStarted -> WatchStatus.NotStarted
    SeriesStatus.InProgress -> WatchStatus.InProgress
    SeriesStatus.Watched -> WatchStatus.Watched
}

private val CombinedGenres =
    mapOf(
        "Sci-Fi & Fantasy" to listOf("Science Fiction", "Fantasy"),
        "Action & Adventure" to listOf("Action", "Adventure"),
        "War & Politics" to listOf("War", "Politics"),
        "Sci-Fi" to listOf("Science Fiction"),
        "Kids" to listOf("Family"),
    )

private val Articles = listOf("The ", "A ", "An ", "De ", "Het ", "Een ")

private const val NON_LETTER = "#"
private const val DECADE = 10
private const val DAY_MS = 24L * 60 * 60 * 1000
private const val WEEK_DAYS = 7
private const val MONTH_DAYS = 30
private const val SHORT_MS = 2L * 60 * 60 * 1000
private const val LONG_MS = 3L * 60 * 60 * 1000
