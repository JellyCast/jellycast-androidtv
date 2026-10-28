package nl.jellycast.mobile.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Root destinations: signed out or the signed-in shell with its three tabs. */
@Serializable
data object SignIn : NavKey

@Serializable
data object Main : NavKey

/** Pages pushed on a tab's own back stack. Keys hold ids only. */
@Serializable
sealed interface Route : NavKey

@Serializable
data object TabRoot : Route

@Serializable
data class MovieRoute(val movieId: String) : Route

@Serializable
data class SeriesRoute(val seriesId: String) : Route

@Serializable
data class PersonRoute(val personId: String, val name: String) : Route

@Serializable
data class DownloadedSeriesRoute(val seriesId: String) : Route

@Serializable
data object ListsRoute : Route

@Serializable
data class ListRoute(val listId: String) : Route
