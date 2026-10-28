package nl.jellycast.mobile.core.library

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Series

enum class SeriesStatus { NotStarted, InProgress, Watched }

/** One season of a series: how many episodes it has and how many of them are watched. */
data class SeasonSummary(val number: Int, val episodeCount: Int, val watchedCount: Int) {
    val isWatched: Boolean get() = episodeCount > 0 && watchedCount == episodeCount
}

/** One series' episodes in air order, what was watched last and what comes next. */
data class SeriesProgress(
    val series: Series,
    /** Season ascending with Specials (0 or none) last, then episode number. */
    val episodes: List<Episode>,
    val lastTouched: Episode?,
    /** The episode to continue with; `null` when nothing was touched or all after it is watched. */
    val next: Episode?,
    val status: SeriesStatus,
) {
    /** Season numbers with Specials last. */
    val seasons: List<Int> by lazy { seasonSummaries.map { it.number } }

    /** Per season, in the order of [seasons]. */
    val seasonSummaries: List<SeasonSummary> by lazy {
        episodes
            .groupBy { it.seasonNumber ?: 0 }
            .map { (number, inSeason) ->
                SeasonSummary(number, inSeason.size, inSeason.count { it.isPlayed })
            }.sortedWith(compareBy({ it.number == 0 }, { it.number }))
    }

    val watchedCount: Int by lazy { episodes.count { it.isPlayed } }

    val lastTouchedAt: Long? get() = lastTouched?.lastPlayedAt
}

data class NewEntry(val item: NewItem, val addedAtMs: Long)

sealed interface NewItem {
    data class NewMovie(val movie: Movie) : NewItem

    /** [count] episodes added in the window; [isNewSeason] when they start a season not seen before. */
    data class NewSeries(val series: Series, val count: Int, val isNewSeason: Boolean) : NewItem
}

/** Watch status of a single movie, episode or series. */
enum class WatchStatus { NotStarted, InProgress, Watched }

/**
 * Every list derived from a snapshot, as iOS `LibraryIndex`, computed once per snapshot. Home,
 * the Library and the series page read it.
 */
class LibraryIndex(val snapshot: LibrarySnapshot, private val nowMs: Long) {
    val progressBySeries: Map<String, SeriesProgress> by lazy {
        snapshot.series.associate { series ->
            series.id to progressOf(series, snapshot.episodesBySeries[series.id].orEmpty())
        }
    }

    /**
     * Movies in progress and each touched series' next episode, most recently touched first. A
     * next episode added after the series was last watched counts from when it was added.
     */
    val continueWatching: List<PlayableItem> by lazy {
        val movies =
            snapshot.movies
                .filter { it.isInProgress }
                .map { PlayableItem.MovieItem(it) to (it.lastPlayedAt ?: 0L) }
        val episodes =
            progressBySeries.values.mapNotNull { progress ->
                val next = progress.next ?: return@mapNotNull null
                val touched = progress.lastTouchedAt ?: return@mapNotNull null
                PlayableItem.EpisodeItem(next) to maxOf(touched, next.addedAt ?: 0L)
            }
        (movies + episodes).sortedByDescending { it.second }.map { it.first }
    }

    /** Unwatched movies and not fully watched series with episodes added in the last 30 days. */
    val newEntries: List<NewEntry> by lazy {
        val since = nowMs - NEW_WINDOW_MS
        val movies =
            snapshot.movies
                .filter { !it.isPlayed && (it.addedAt ?: 0L) >= since }
                .map { NewEntry(NewItem.NewMovie(it), it.addedAt ?: 0L) }
        val series =
            progressBySeries.values.mapNotNull { progress ->
                if (progress.status == SeriesStatus.Watched) return@mapNotNull null
                val added = progress.episodes.filter { (it.addedAt ?: 0L) >= since }
                if (added.isEmpty()) return@mapNotNull null
                val addedIds = added.mapTo(HashSet()) { it.id }
                val olderSeasons =
                    progress.episodes
                        .filter { it.id !in addedIds }
                        .mapNotNull { it.seasonNumber }
                        .toSet()
                val isNewSeason =
                    olderSeasons.isNotEmpty() &&
                        added.any { it.seasonNumber != null && it.seasonNumber !in olderSeasons }
                NewEntry(
                    NewItem.NewSeries(progress.series, added.size, isNewSeason),
                    added.maxOf { it.addedAt ?: 0L },
                )
            }
        (movies + series).sortedByDescending { it.addedAtMs }
    }

    /** Series nobody started, most recently added to first. */
    val notStarted: List<Series> by lazy {
        progressBySeries.values
            .filter { it.status == SeriesStatus.NotStarted }
            .map { it.series }
            .sortedByDescending { it.lastEpisodeAddedAt ?: 0L }
    }

    fun progress(seriesId: String): SeriesProgress? = progressBySeries[seriesId]

    /** The episode after [episode] in air order, across season ends, skipping Specials. */
    fun nextEpisode(episode: Episode): Episode? {
        val regular = progressBySeries[episode.seriesId]?.episodes.orEmpty().filter { it.isRegular }
        val index = regular.indexOfFirst { it.id == episode.id }
        return if (index < 0) null else regular.getOrNull(index + 1)
    }

    /** A movie or an episode by id. */
    fun playable(id: String): PlayableItem? {
        snapshot.moviesById[id]?.let { return PlayableItem.MovieItem(it) }
        return snapshot.episodesById[id]?.let { PlayableItem.EpisodeItem(it) }
    }

    /** The other movies of the same TMDb collection, in release order. */
    fun collection(movie: Movie): List<Movie> {
        val collectionId = movie.externalIds.tmdbCollection ?: return emptyList()
        return snapshot.movies
            .filter { it.externalIds.tmdbCollection == collectionId && it.id != movie.id }
            .sortedBy { it.year ?: 0 }
    }

    fun status(movie: Movie): WatchStatus = when {
        movie.isPlayed -> WatchStatus.Watched
        movie.isInProgress -> WatchStatus.InProgress
        else -> WatchStatus.NotStarted
    }

    fun status(series: Series): WatchStatus = when (progressBySeries[series.id]?.status) {
        SeriesStatus.Watched -> WatchStatus.Watched
        SeriesStatus.InProgress -> WatchStatus.InProgress
        SeriesStatus.NotStarted, null -> WatchStatus.NotStarted
    }

    /** Episodes of [seriesId] added in the last [days] days. */
    fun newEpisodeCount(seriesId: String, days: Int): Int {
        val since = nowMs - days * DAY_MS
        return progressBySeries[seriesId]?.episodes?.count { (it.addedAt ?: 0L) >= since } ?: 0
    }

    /** The sum of the episode runtimes; the Library sorts a series by it on "Length". */
    fun runtimeMs(series: Series): Long =
        progressBySeries[series.id]?.episodes.orEmpty().sumOf { it.runtimeMs ?: 0L }

    /** Canonical genres of all movies and series with their title count, most titles first. */
    val genreCounts: List<Pair<String, Int>> by lazy {
        genreCountsOf(snapshot.movies.map { it.genres } + snapshot.series.map { it.genres })
    }

    val movieGenreCounts: List<Pair<String, Int>> by lazy {
        genreCountsOf(snapshot.movies.map { it.genres })
    }

    val seriesGenreCounts: List<Pair<String, Int>> by lazy {
        genreCountsOf(snapshot.series.map { it.genres })
    }
}

private fun genreCountsOf(genreLists: List<List<String>>): List<Pair<String, Int>> = genreLists
    .flatMap { LibraryGenres.canonical(it) }
    .groupingBy { it }
    .eachCount()
    .toList()
    .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

private val Episode.isRegular: Boolean get() = (seasonNumber ?: 0) > 0

private val Episode.isInProgress: Boolean get() = !isPlayed && resumePositionMs > 0

private val Movie.isInProgress: Boolean get() = !isPlayed && resumePositionMs > 0

/** Season ascending with Specials (season 0 or none) last, then episode number. */
internal val AirOrder: Comparator<Episode> =
    compareBy({ !it.isRegular }, { it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 })

internal fun progressOf(series: Series, episodes: List<Episode>): SeriesProgress {
    val ordered = episodes.sortedWith(AirOrder)
    val regular = ordered.filter { it.isRegular }
    val lastTouched =
        regular
            .filter { it.lastPlayedAt != null && (it.isPlayed || it.isInProgress) }
            .maxByOrNull { it.lastPlayedAt ?: 0L }
    val next =
        when {
            lastTouched == null -> null
            lastTouched.isInProgress -> lastTouched
            else -> regular.drop(regular.indexOf(lastTouched) + 1).firstOrNull { !it.isPlayed }
        }
    val status =
        when {
            ordered.isNotEmpty() && ordered.all { it.isPlayed } -> SeriesStatus.Watched
            ordered.any { it.isPlayed || it.isInProgress } -> SeriesStatus.InProgress
            else -> SeriesStatus.NotStarted
        }
    return SeriesProgress(series, ordered, lastTouched, next, status)
}

private const val DAY_MS = 24L * 60 * 60 * 1000

private const val NEW_WINDOW_MS = 30 * DAY_MS
