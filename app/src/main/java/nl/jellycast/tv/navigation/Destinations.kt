package nl.jellycast.tv.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ServerSetup : NavKey

@Serializable
data object Login : NavKey

@Serializable
data object Home : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object PlaybackSettings : NavKey

@Serializable
data class MovieDetail(val movieId: String) : NavKey

@Serializable
data class SeriesDetail(val seriesId: String, val episodeId: String? = null) : NavKey

@Serializable
data class Player(
    val itemId: String,
    val startPositionMs: Long,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
) : NavKey
