package nl.jellycast.tv.testing

import nl.jellycast.tv.core.model.Artwork
import nl.jellycast.tv.core.model.AudioTrack
import nl.jellycast.tv.core.model.Episode
import nl.jellycast.tv.core.model.MediaTech
import nl.jellycast.tv.core.model.Movie
import nl.jellycast.tv.core.model.PlayMethod
import nl.jellycast.tv.core.model.PlaybackPlan
import nl.jellycast.tv.core.model.Series
import nl.jellycast.tv.core.model.Spine
import nl.jellycast.tv.core.model.VideoInfo

fun testMovie(
    id: String,
    title: String = "Movie $id",
    genres: List<String> = emptyList(),
    resumePositionMs: Long = 0,
    runtimeMs: Long? = 7_200_000,
    lastPlayedAt: Long? = null,
    addedAt: Long? = null,
    isPlayed: Boolean = false,
) = Movie(
    id = id,
    title = title,
    year = 2020,
    runtimeMs = runtimeMs,
    overview = null,
    genres = genres,
    ageRating = null,
    resumePositionMs = resumePositionMs,
    isPlayed = isPlayed,
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt,
    artwork = Artwork(),
    spine = Spine(posterUrl = null, logoUrl = null, color = 0xFF000000, isLight = false),
    tech = MediaTech(),
)

fun testSeries(
    id: String,
    title: String = "Serie $id",
    genres: List<String> = emptyList(),
    lastEpisodeAddedAt: Long? = null,
    isPlayed: Boolean = false,
    hasPlayedEpisodes: Boolean = isPlayed,
) = Series(
    id = id,
    title = title,
    year = 2020,
    overview = null,
    genres = genres,
    ageRating = null,
    seasonCount = 1,
    lastEpisodeAddedAt = lastEpisodeAddedAt,
    artwork = Artwork(),
    isPlayed = isPlayed,
    hasPlayedEpisodes = hasPlayedEpisodes,
)

fun testEpisode(
    id: String,
    seriesId: String = "s-$id",
    resumePositionMs: Long = 0,
    season: Int? = 1,
    number: Int? = 2,
    isPlayed: Boolean = false,
    seasonId: String? = null,
    lastPlayedAt: Long? = null,
    addedAt: Long? = null,
    touchedAt: Long? = lastPlayedAt,
) = Episode(
    id = id,
    seriesId = seriesId,
    seasonId = seasonId,
    seriesTitle = "Serie $seriesId",
    seasonNumber = season,
    episodeNumber = number,
    isPlayed = isPlayed,
    title = "Episode $id",
    overview = null,
    runtimeMs = 3_000_000,
    resumePositionMs = resumePositionMs,
    artwork = Artwork(),
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt,
    touchedAt = touchedAt,
)

// Test data (addedAt 0–10) falls within the 30 days of the New shelf.
const val NOW = 10L

fun testPlan(
    itemId: String = "m1",
    startPositionMs: Long = 0,
    playMethod: PlayMethod = PlayMethod.DirectPlay,
    video: VideoInfo? = null,
    audioTracks: List<AudioTrack> = emptyList(),
    audioStreamIndex: Int? = null,
    transcodeReasons: List<String> = emptyList(),
) = PlaybackPlan(
    itemId = itemId,
    title = itemId,
    mediaSourceId = itemId,
    playSessionId = null,
    streamUrl = "",
    playMethod = playMethod,
    transcodeReasons = transcodeReasons,
    container = "mkv",
    video = video,
    audioTracks = audioTracks,
    subtitleTracks = emptyList(),
    audioStreamIndex = audioStreamIndex,
    subtitleStreamIndex = null,
    startPositionMs = startPositionMs,
)
