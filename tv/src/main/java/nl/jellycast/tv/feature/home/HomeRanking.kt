package nl.jellycast.tv.feature.home

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie

enum class SuggestionReason { ContinueWatching, NewEpisode, NextEpisode, RecentlyAdded, ForTonight }

/** A suggestion before its series has been fetched. */
sealed interface Candidate {
    val id: String
    val reason: SuggestionReason

    data class MovieCandidate(val movie: Movie, override val reason: SuggestionReason) :
        Candidate {
        override val id: String get() = movie.id
    }

    data class EpisodeCandidate(val episode: Episode, override val reason: SuggestionReason) :
        Candidate {
        override val id: String get() = episode.id
    }
}

const val HOME_STACK_SIZE = 5

private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val RECENTLY_IN_PROGRESS_WINDOW_MS = 3 * DAY_MS
private const val NEW_EPISODE_MAX_AGE_MS = 14 * DAY_MS
private const val STILL_WATCHING_WINDOW_MS = 30 * DAY_MS
private const val RECENTLY_ADDED_MOVIE_MAX_AGE_MS = 30 * DAY_MS

/**
 * Ranks what to suggest on Home, in this order of groups:
 * [recentlyInProgress], [newEpisodes], [stillWatching], [recentlyAddedMovies], then the fallback
 * (older in-progress items, then [randomMovie]). Each item appears once, at its first group.
 */
fun rankHome(
    nextUp: List<Episode>,
    resumeMovies: List<Movie>,
    newMovies: List<Movie>,
    randomMovie: Movie?,
    nowMs: Long,
): List<Candidate> {
    val continuing = continuingByLastTouch(nextUp, resumeMovies)
    val groups =
        listOf(
            recentlyInProgress(continuing, nowMs),
            newEpisodes(nextUp, nowMs),
            stillWatching(continuing, nowMs),
            recentlyAddedMovies(newMovies, nowMs),
            continuing.map { it.candidate },
            listOfNotNull(randomMovie?.let { forTonight(it) }),
        )
    val ranked = LinkedHashMap<String, Candidate>()
    groups.flatten().forEach { ranked.putIfAbsent(it.id, it) }
    return ranked.values.take(HOME_STACK_SIZE)
}

private data class Continuing(val candidate: Candidate, val touchedAt: Long?) {
    fun touchedWithin(windowMs: Long, nowMs: Long): Boolean =
        touchedAt != null && nowMs - touchedAt <= windowMs
}

private fun continuingByLastTouch(nextUp: List<Episode>, resumeMovies: List<Movie>) = (
    resumeMovies.map {
        Continuing(Candidate.MovieCandidate(it, reasonOf(it)), it.lastPlayedAt)
    } +
        nextUp.map { Continuing(Candidate.EpisodeCandidate(it, reasonOf(it)), it.touchedAt) }
    ).sortedByDescending { it.touchedAt ?: 0 }

private fun recentlyInProgress(continuing: List<Continuing>, nowMs: Long): List<Candidate> =
    continuing
        .filter {
            it.candidate.reason == SuggestionReason.ContinueWatching &&
                it.touchedWithin(RECENTLY_IN_PROGRESS_WINDOW_MS, nowMs)
        }.map { it.candidate }

private fun newEpisodes(nextUp: List<Episode>, nowMs: Long): List<Candidate> = nextUp
    .filter { isNewEpisode(it, nowMs) }
    .sortedByDescending { it.addedAt ?: 0 }
    .map { Candidate.EpisodeCandidate(it, SuggestionReason.NewEpisode) }

private fun stillWatching(continuing: List<Continuing>, nowMs: Long): List<Candidate> = continuing
    .filter { it.touchedWithin(STILL_WATCHING_WINDOW_MS, nowMs) }
    .map { it.candidate }

private fun recentlyAddedMovies(newMovies: List<Movie>, nowMs: Long): List<Candidate> = newMovies
    .filter { isRecentlyAddedUnwatched(it, nowMs) }
    .sortedByDescending { it.addedAt ?: 0 }
    .map { Candidate.MovieCandidate(it, SuggestionReason.RecentlyAdded) }

private fun isRecentlyAddedUnwatched(movie: Movie, nowMs: Long): Boolean {
    val addedAt = movie.addedAt ?: return false
    return !movie.isPlayed &&
        movie.resumePositionMs == 0L &&
        nowMs - addedAt <= RECENTLY_ADDED_MOVIE_MAX_AGE_MS
}

private fun forTonight(movie: Movie): Candidate =
    Candidate.MovieCandidate(movie, SuggestionReason.ForTonight)

private fun reasonOf(movie: Movie): SuggestionReason = if (movie.resumePositionMs > 0) {
    SuggestionReason.ContinueWatching
} else {
    SuggestionReason.RecentlyAdded
}

private fun reasonOf(episode: Episode): SuggestionReason = if (episode.resumePositionMs > 0) {
    SuggestionReason.ContinueWatching
} else {
    SuggestionReason.NextEpisode
}

private fun isNewEpisode(episode: Episode, nowMs: Long): Boolean {
    val addedAt = episode.addedAt ?: return false
    val previousWatchedAt = episode.touchedAt ?: return false
    return episode.resumePositionMs == 0L &&
        !episode.isPlayed &&
        addedAt > previousWatchedAt &&
        nowMs - addedAt <= NEW_EPISODE_MAX_AGE_MS
}
