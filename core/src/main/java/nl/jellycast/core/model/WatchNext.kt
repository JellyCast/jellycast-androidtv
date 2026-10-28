package nl.jellycast.core.model

/** [Continue]: started, with a position. [Next]: the next unwatched episode of a series. */
enum class WatchNextKind { Continue, Next }

/** One card in the "Continue watching" row of the Google TV home screen. */
data class WatchNextEntry(
    val itemId: String,
    val kind: WatchNextKind,
    val title: String,
    val episode: EpisodeInfo?,
    val durationMs: Long?,
    val positionMs: Long,
    val lastEngagementMs: Long,
    val imageUrl: String?,
    val playLink: String,
) {
    data class EpisodeInfo(val title: String, val seasonNumber: Int?, val episodeNumber: Int?)
}

/** The same picks as the home stack: next-up episodes and started movies, most recent first. */
fun watchNextEntries(nextUp: List<Episode>, resumeMovies: List<Movie>): List<WatchNextEntry> {
    val episodes =
        nextUp.map { episode ->
            WatchNextEntry(
                itemId = episode.id,
                kind = episode.watchNextKind(),
                title = episode.seriesTitle,
                episode =
                    WatchNextEntry.EpisodeInfo(
                        episode.title,
                        episode.seasonNumber,
                        episode.episodeNumber,
                    ),
                durationMs = episode.runtimeMs,
                positionMs = episode.resumePositionMs,
                lastEngagementMs = episode.touchedAt ?: episode.lastPlayedAt ?: 0,
                imageUrl =
                    episode.artwork.landscape ?: episode.stillUrl ?: episode.artwork.backdrop,
                playLink = playLink(episode.id, episode.resumePositionMs),
            )
        }
    val movies =
        resumeMovies.map { movie ->
            WatchNextEntry(
                itemId = movie.id,
                kind = WatchNextKind.Continue,
                title = movie.title,
                episode = null,
                durationMs = movie.runtimeMs,
                positionMs = movie.resumePositionMs,
                lastEngagementMs = movie.lastPlayedAt ?: 0,
                imageUrl = movie.artwork.landscape ?: movie.artwork.backdrop,
                playLink = playLink(movie.id, movie.resumePositionMs),
            )
        }
    return (episodes + movies)
        .distinctBy { it.itemId }
        .sortedByDescending { it.lastEngagementMs }
        .take(WATCH_NEXT_LIMIT)
}

private fun Episode.watchNextKind(): WatchNextKind =
    if (resumePositionMs > 0) WatchNextKind.Continue else WatchNextKind.Next

private const val WATCH_NEXT_LIMIT = 10
