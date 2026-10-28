package nl.jellycast.mobile.core.library

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Series

enum class SeriesStatus { NotStarted, InProgress, Watched }

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
    val seasons: List<Int> by lazy {
        episodes.map { it.seasonNumber ?: 0 }.distinct().sortedWith(compareBy({ it == 0 }, { it }))
    }
}

data class NewEntry(val item: NewItem, val addedAtMs: Long)

sealed interface NewItem {
    data class NewMovie(val movie: Movie) : NewItem

    /** [count] episodes added in the window; [isNewSeason] when they start a season not seen before. */
    data class NewSeries(val series: Series, val count: Int, val isNewSeason: Boolean) : NewItem
}

/**
 * Every list derived from a snapshot, as iOS `LibraryIndex`. Owner: the library worker; the
 * home, library and series screens read it.
 */
class LibraryIndex(val snapshot: LibrarySnapshot, private val nowMs: Long) {
    val progressBySeries: Map<String, SeriesProgress> by lazy {
        snapshot.series.associate { series ->
            series.id to progressOf(series, snapshot.episodesBySeries[series.id].orEmpty())
        }
    }

    /** Movies in progress and each touched series' next episode, most recent first. */
    val continueWatching: List<PlayableItem> by lazy {
        val movies =
            snapshot.movies
                .filter { !it.isPlayed && it.resumePositionMs > 0 }
                .map { PlayableItem.MovieItem(it) to (it.lastPlayedAt ?: 0L) }
        val episodes =
            progressBySeries.values.mapNotNull { progress ->
                val next = progress.next ?: return@mapNotNull null
                val touched = progress.lastTouched?.lastPlayedAt ?: 0L
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
                val olderSeasons = (progress.episodes - added.toSet()).mapNotNull {
                    it.seasonNumber
                }.toSet()
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

    /** Series nobody started, newest first. */
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
}

private val Episode.isRegular: Boolean get() = (seasonNumber ?: 0) > 0

private val Episode.isInProgress: Boolean get() = !isPlayed && resumePositionMs > 0

internal fun progressOf(series: Series, episodes: List<Episode>): SeriesProgress {
    val ordered =
        episodes.sortedWith(
            compareBy<Episode>({
                !it.isRegular
            }, { it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }),
        )
    val regular = ordered.filter { it.isRegular }
    val lastTouched =
        regular
            .filter { it.isPlayed || it.isInProgress }
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

private const val NEW_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
