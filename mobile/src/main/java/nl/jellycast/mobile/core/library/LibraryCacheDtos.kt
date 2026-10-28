package nl.jellycast.mobile.core.library

import kotlinx.serialization.Serializable
import nl.jellycast.core.model.AgeRating
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.DynamicRange
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Resolution
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.Spine

// The on-disk form of a snapshot, apart from the models so a model change can't silently break
// reading an older file: bump the format version instead.
internal const val CACHE_FORMAT_VERSION = 1

@Serializable
internal data class CachedLibrary(
    val formatVersion: Int,
    val serverUrl: String,
    val userId: String,
    val syncedAtMs: Long,
    val movies: List<CachedMovie>,
    val series: List<CachedSeries>,
    val episodes: List<CachedEpisode>,
)

@Serializable
internal data class CachedArtwork(
    val poster: String? = null,
    val landscape: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val backdrops: List<String> = emptyList(),
)

@Serializable
internal data class CachedSpine(
    val posterUrl: String? = null,
    val logoUrl: String? = null,
    val color: Long,
    val isLight: Boolean,
    val darkColor: Long,
)

@Serializable
internal data class CachedTech(
    val resolution: String? = null,
    val dynamicRange: String? = null,
    val hasAtmos: Boolean = false,
)

@Serializable
internal data class CachedIds(
    val imdb: String? = null,
    val tmdb: String? = null,
    val tmdbCollection: String? = null,
)

@Serializable
internal data class CachedMovie(
    val id: String,
    val title: String,
    val year: Int? = null,
    val runtimeMs: Long? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val ageRating: String? = null,
    val resumePositionMs: Long = 0,
    val isPlayed: Boolean = false,
    val lastPlayedAt: Long? = null,
    val addedAt: Long? = null,
    val artwork: CachedArtwork,
    val spine: CachedSpine,
    val tech: CachedTech = CachedTech(),
    val communityRating: Float? = null,
    val criticRating: Float? = null,
    val ids: CachedIds = CachedIds(),
)

@Serializable
internal data class CachedSeries(
    val id: String,
    val title: String,
    val year: Int? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val ageRating: String? = null,
    val seasonCount: Int? = null,
    val lastEpisodeAddedAt: Long? = null,
    val artwork: CachedArtwork,
    val isPlayed: Boolean = false,
    val hasPlayedEpisodes: Boolean = false,
    val communityRating: Float? = null,
    val ids: CachedIds = CachedIds(),
)

@Serializable
internal data class CachedEpisode(
    val id: String,
    val seriesId: String,
    val seriesTitle: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val title: String,
    val overview: String? = null,
    val runtimeMs: Long? = null,
    val resumePositionMs: Long = 0,
    val artwork: CachedArtwork,
    val isPlayed: Boolean = false,
    val stillUrl: String? = null,
    val seasonId: String? = null,
    val lastPlayedAt: Long? = null,
    val addedAt: Long? = null,
    val communityRating: Float? = null,
    val ids: CachedIds = CachedIds(),
)

internal fun LibrarySnapshot.toCached(serverUrl: String, userId: String) = CachedLibrary(
    formatVersion = CACHE_FORMAT_VERSION,
    serverUrl = serverUrl,
    userId = userId,
    syncedAtMs = syncedAtMs,
    movies = movies.map { it.toCached() },
    series = series.map { it.toCached() },
    episodes = episodes.map { it.toCached() },
)

internal fun CachedLibrary.toSnapshot() = LibrarySnapshot(
    movies = movies.map { it.toMovie() },
    series = series.map { it.toSeries() },
    episodes = episodes.map { it.toEpisode() },
    syncedAtMs = syncedAtMs,
)

private fun Artwork.toCached() = CachedArtwork(poster, landscape, backdrop, logo, backdrops)

private fun CachedArtwork.toArtwork() = Artwork(poster, landscape, backdrop, logo, backdrops)

private fun ExternalIds.toCached() = CachedIds(imdb, tmdb, tmdbCollection)

private fun CachedIds.toIds() = ExternalIds(imdb, tmdb, tmdbCollection)

private fun Movie.toCached() = CachedMovie(
    id = id,
    title = title,
    year = year,
    runtimeMs = runtimeMs,
    overview = overview,
    genres = genres,
    ageRating = ageRating?.label,
    resumePositionMs = resumePositionMs,
    isPlayed = isPlayed,
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt,
    artwork = artwork.toCached(),
    spine = CachedSpine(
        spine.posterUrl,
        spine.logoUrl,
        spine.color,
        spine.isLight,
        spine.darkColor,
    ),
    tech = CachedTech(tech.resolution?.name, tech.dynamicRange?.name, tech.hasAtmos),
    communityRating = communityRating,
    criticRating = criticRating,
    ids = externalIds.toCached(),
)

private fun CachedMovie.toMovie() = Movie(
    id = id,
    title = title,
    year = year,
    runtimeMs = runtimeMs,
    overview = overview,
    genres = genres,
    ageRating = ageRating?.let(::AgeRating),
    resumePositionMs = resumePositionMs,
    isPlayed = isPlayed,
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt,
    artwork = artwork.toArtwork(),
    spine = Spine(spine.posterUrl, spine.logoUrl, spine.color, spine.isLight, spine.darkColor),
    tech =
        MediaTech(
            resolution = Resolution.entries.firstOrNull { it.name == tech.resolution },
            dynamicRange = DynamicRange.entries.firstOrNull { it.name == tech.dynamicRange },
            hasAtmos = tech.hasAtmos,
        ),
    communityRating = communityRating,
    criticRating = criticRating,
    externalIds = ids.toIds(),
)

private fun Series.toCached() = CachedSeries(
    id = id,
    title = title,
    year = year,
    overview = overview,
    genres = genres,
    ageRating = ageRating?.label,
    seasonCount = seasonCount,
    lastEpisodeAddedAt = lastEpisodeAddedAt,
    artwork = artwork.toCached(),
    isPlayed = isPlayed,
    hasPlayedEpisodes = hasPlayedEpisodes,
    communityRating = communityRating,
    ids = externalIds.toCached(),
)

private fun CachedSeries.toSeries() = Series(
    id = id,
    title = title,
    year = year,
    overview = overview,
    genres = genres,
    ageRating = ageRating?.let(::AgeRating),
    seasonCount = seasonCount,
    lastEpisodeAddedAt = lastEpisodeAddedAt,
    artwork = artwork.toArtwork(),
    isPlayed = isPlayed,
    hasPlayedEpisodes = hasPlayedEpisodes,
    communityRating = communityRating,
    externalIds = ids.toIds(),
)

private fun Episode.toCached() = CachedEpisode(
    id = id,
    seriesId = seriesId,
    seriesTitle = seriesTitle,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
    overview = overview,
    runtimeMs = runtimeMs,
    resumePositionMs = resumePositionMs,
    artwork = artwork.toCached(),
    isPlayed = isPlayed,
    stillUrl = stillUrl,
    seasonId = seasonId,
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt,
    communityRating = communityRating,
    ids = externalIds.toCached(),
)

private fun CachedEpisode.toEpisode() = Episode(
    id = id,
    seriesId = seriesId,
    seriesTitle = seriesTitle,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
    overview = overview,
    runtimeMs = runtimeMs,
    resumePositionMs = resumePositionMs,
    artwork = artwork.toArtwork(),
    isPlayed = isPlayed,
    stillUrl = stillUrl,
    seasonId = seasonId,
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt,
    communityRating = communityRating,
    externalIds = ids.toIds(),
)
