package nl.jellycast.tv.feature.settings

import nl.jellycast.tv.core.model.AppLanguage
import nl.jellycast.tv.core.model.MovieView
import nl.jellycast.tv.core.playback.DEFAULT_MAX_BITRATE

data class SettingsUiState(
    val versionName: String,
    val versionCode: Int,
    val serverName: String = "",
    val userName: String = "",
    val isLoggingOut: Boolean = false,
    val burnInAss: Boolean = false,
    /** `null` means no limit. */
    val maxBitrateMbps: Int? = null,
    val movieView: MovieView = MovieView.Detail,
    /** `null` only in previews and tests. */
    val language: AppLanguage? = null,
)

internal fun Int.toMbpsOrNull(): Int? = if (this >= DEFAULT_MAX_BITRATE) null else this / 1_000_000
