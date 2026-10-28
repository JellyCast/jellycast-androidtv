package nl.jellycast.core.model

/**
 * Image URLs sized for display; `null` when the server has no such image. [backdrop] is the one
 * of [backdrops] to show.
 */
data class Artwork(
    val poster: String? = null,
    val landscape: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val backdrops: List<String> = listOfNotNull(backdrop),
) {
    fun withBackdrop(index: Int): Artwork = copy(backdrop = backdrops.getOrNull(index) ?: backdrop)
}

enum class Resolution { UHD, HD, SD }

enum class DynamicRange { DolbyVision, Hdr10Plus, Hdr10, Hlg }

data class MediaTech(
    val resolution: Resolution? = null,
    val dynamicRange: DynamicRange? = null,
    val hasAtmos: Boolean = false,
)

/** The ids other sites know the item by; [tmdbCollection] groups the films of one collection. */
data class ExternalIds(
    val imdb: String? = null,
    val tmdb: String? = null,
    val tmdbCollection: String? = null,
)

/** Dutch Kijkwijzer age as a number, otherwise the server's code (e.g. "R"). */
data class AgeRating(val label: String)

data class Spine(
    val posterUrl: String?,
    val logoUrl: String?,
    val color: Long,
    val isLight: Boolean,
    // Dark variant of the hue even for a light spine: background behind light text.
    val darkColor: Long = color,
)

data class Movie(
    val id: String,
    val title: String,
    val year: Int?,
    val runtimeMs: Long?,
    val overview: String?,
    val genres: List<String>,
    val ageRating: AgeRating?,
    val resumePositionMs: Long,
    val isPlayed: Boolean = false,
    val lastPlayedAt: Long?,
    val addedAt: Long?,
    val artwork: Artwork,
    val spine: Spine,
    val tech: MediaTech,
    val communityRating: Float? = null,
    /** Rotten Tomatoes score, 0–100. */
    val criticRating: Float? = null,
    val externalIds: ExternalIds = ExternalIds(),
)

data class Series(
    val id: String,
    val title: String,
    val year: Int?,
    val overview: String?,
    val genres: List<String>,
    val ageRating: AgeRating?,
    val seasonCount: Int?,
    val lastEpisodeAddedAt: Long?,
    val artwork: Artwork,
    val isPlayed: Boolean = false,
    // In-progress episodes do not count.
    val hasPlayedEpisodes: Boolean = false,
    val communityRating: Float? = null,
    val externalIds: ExternalIds = ExternalIds(),
)

data class Episode(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val title: String,
    val overview: String?,
    val runtimeMs: Long?,
    val resumePositionMs: Long,
    // `landscape` is the series thumb (or still); `backdrop` and `logo` are the series'.
    val artwork: Artwork,
    val isPlayed: Boolean = false,
    val stillUrl: String? = null,
    /** The season's own landscape thumb, when the server has one. */
    val seasonThumbUrl: String? = null,
    val seasonId: String? = null,
    val lastPlayedAt: Long? = null,
    val addedAt: Long? = null,
    // Continue watching only: when the series was last touched. For a next-up episode that is
    // the previous episode's time.
    val touchedAt: Long? = null,
    val communityRating: Float? = null,
    val externalIds: ExternalIds = ExternalIds(),
)

/** Merely opened (position 0) does not count as started. */
val Episode.isStarted: Boolean
    get() = isPlayed || resumePositionMs > 0

/**
 * The most recently touched episode if in progress, else the first unwatched one after it; `null`
 * when nothing was started or everything after it is watched. Ties (a whole season marked at once)
 * or missing times go to the highest episode. [episodes] may be a subset containing that episode.
 */
fun continueEpisode(episodes: List<Episode>): Episode? {
    val ordered = episodes.regularInOrder()
    val last =
        ordered
            .withIndex()
            .filter { it.value.isStarted }
            .maxWithOrNull(compareBy({ it.value.lastPlayedAt ?: 0 }, { it.index }))
            ?: return null
    if (!last.value.isPlayed) return last.value
    return ordered.drop(last.index + 1).firstOrNull { !it.isPlayed }
}

fun progressOf(resumePositionMs: Long, runtimeMs: Long?): Float? {
    if (resumePositionMs <= 0 || runtimeMs == null || runtimeMs <= 0) return null
    return (resumePositionMs.toFloat() / runtimeMs).coerceIn(0f, 1f)
}

val Movie.progress: Float? get() = progressOf(resumePositionMs, runtimeMs)

val Episode.progress: Float? get() = progressOf(resumePositionMs, runtimeMs)

fun remainingMs(resumePositionMs: Long, runtimeMs: Long?): Long? {
    if (resumePositionMs <= 0 || runtimeMs == null) return null
    return (runtimeMs - resumePositionMs).coerceAtLeast(0)
}

sealed interface PlayableItem {
    val id: String

    data class MovieItem(val movie: Movie) : PlayableItem {
        override val id: String get() = movie.id
    }

    data class EpisodeItem(val episode: Episode) : PlayableItem {
        override val id: String get() = episode.id
    }
}

/** Specials are skipped. */
fun nextEpisodeAfter(current: Episode, episodes: List<Episode>): Episode? {
    val ordered = episodes.regularInOrder()
    val index = ordered.indexOfFirst { it.id == current.id }
    return if (index >= 0) ordered.getOrNull(index + 1) else null
}

private fun List<Episode>.regularInOrder(): List<Episode> = filter { (it.seasonNumber ?: 0) > 0 }
    .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber ?: Int.MAX_VALUE }))
