package nl.jellycast.mobile.core.library

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie

/**
 * [itemIds] may name movies, episodes, seasons or series; a season or series marks all its
 * episodes. Unwatching clears the position, watching also sets the last played time.
 */
internal fun LibrarySnapshot.withPlayed(
    itemIds: Collection<String>,
    played: Boolean,
    nowMs: Long,
): LibrarySnapshot {
    val ids = itemIds.toSet()
    fun Movie.marked() = copy(
        isPlayed = played,
        resumePositionMs = 0,
        lastPlayedAt = if (played) nowMs else lastPlayedAt,
    )
    fun Episode.marked() = copy(
        isPlayed = played,
        resumePositionMs = 0,
        lastPlayedAt = if (played) nowMs else lastPlayedAt,
    )
    return copy(
        movies = movies.map { if (it.id in ids) it.marked() else it },
        series =
            series.map {
                if (it.id in ids) it.copy(isPlayed = played, hasPlayedEpisodes = played) else it
            },
        episodes =
            episodes.map {
                if (it.id in ids || it.seriesId in ids || it.seasonId in ids) it.marked() else it
            },
    )
}

/** A stopped play: [finished] marks it watched and resets the position. */
internal fun LibrarySnapshot.withPlayback(
    itemId: String,
    positionMs: Long,
    finished: Boolean,
    nowMs: Long,
): LibrarySnapshot = copy(
    movies =
        movies.map {
            if (it.id != itemId) {
                it
            } else {
                it.copy(
                    lastPlayedAt = nowMs,
                    isPlayed = finished || it.isPlayed,
                    resumePositionMs = if (finished) 0 else positionMs,
                )
            }
        },
    episodes =
        episodes.map {
            if (it.id != itemId) {
                it
            } else {
                it.copy(
                    lastPlayedAt = nowMs,
                    isPlayed = finished || it.isPlayed,
                    resumePositionMs = if (finished) 0 else positionMs,
                )
            }
        },
)

/** Positions watched offline win over the server's copy only when they are newer. */
fun LibrarySnapshot.withOfflineProgress(pending: Map<String, OfflinePosition>): LibrarySnapshot {
    if (pending.isEmpty()) return this
    fun isNewer(lastPlayedAt: Long?, position: OfflinePosition) =
        lastPlayedAt == null || lastPlayedAt < position.watchedAtMs
    return copy(
        movies =
            movies.map { movie ->
                val position = pending[movie.id]
                if (position == null || !isNewer(movie.lastPlayedAt, position)) {
                    movie
                } else {
                    movie.copy(
                        lastPlayedAt = position.watchedAtMs,
                        isPlayed = position.played || movie.isPlayed,
                        resumePositionMs = position.positionMs,
                    )
                }
            },
        episodes =
            episodes.map { episode ->
                val position = pending[episode.id]
                if (position == null || !isNewer(episode.lastPlayedAt, position)) {
                    episode
                } else {
                    episode.copy(
                        lastPlayedAt = position.watchedAtMs,
                        isPlayed = position.played || episode.isPlayed,
                        resumePositionMs = position.positionMs,
                    )
                }
            },
    )
}
