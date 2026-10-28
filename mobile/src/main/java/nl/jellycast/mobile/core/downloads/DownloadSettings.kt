package nl.jellycast.mobile.core.downloads

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * How downloads look after themselves (Settings → Downloads): watched ones go a day later, and
 * series downloaded with "Download next" can stay ahead.
 */
interface DownloadSettings {
    val removesWatched: StateFlow<Boolean>

    val keepsSeriesAhead: StateFlow<Boolean>

    /** Series downloaded with "Download next"; these stay ahead while [keepsSeriesAhead] is on. */
    val keptSeriesIds: StateFlow<Set<String>>

    fun setRemovesWatched(value: Boolean)

    fun setKeepsSeriesAhead(value: Boolean)

    fun keep(seriesId: String)

    fun stopKeeping(seriesId: String)

    companion object {
        /** How many episodes "Download next" fetches, and how many a kept series stays ahead. */
        const val SERIES_BATCH = 5

        /** How long a watched download stays before it is removed. */
        const val WATCHED_GRACE_MS = 24L * 60 * 60 * 1000
    }
}

class SharedPreferencesDownloadSettings(private val preferences: SharedPreferences) :
    DownloadSettings {
    private val _removesWatched = MutableStateFlow(preferences.getBoolean(REMOVES_WATCHED, true))
    override val removesWatched: StateFlow<Boolean> = _removesWatched.asStateFlow()

    private val _keepsSeriesAhead = MutableStateFlow(preferences.getBoolean(KEEPS_AHEAD, false))
    override val keepsSeriesAhead: StateFlow<Boolean> = _keepsSeriesAhead.asStateFlow()

    private val _keptSeriesIds =
        MutableStateFlow(preferences.getStringSet(KEPT_SERIES, emptySet()).orEmpty().toSet())
    override val keptSeriesIds: StateFlow<Set<String>> = _keptSeriesIds.asStateFlow()

    override fun setRemovesWatched(value: Boolean) {
        _removesWatched.value = value
        preferences.edit().putBoolean(REMOVES_WATCHED, value).apply()
    }

    override fun setKeepsSeriesAhead(value: Boolean) {
        _keepsSeriesAhead.value = value
        preferences.edit().putBoolean(KEEPS_AHEAD, value).apply()
    }

    override fun keep(seriesId: String) = changeKept { it + seriesId }

    override fun stopKeeping(seriesId: String) = changeKept { it - seriesId }

    private fun changeKept(change: (Set<String>) -> Set<String>) {
        _keptSeriesIds.update(change)
        preferences.edit().putStringSet(KEPT_SERIES, _keptSeriesIds.value).apply()
    }

    private companion object {
        const val REMOVES_WATCHED = "removesWatched"
        const val KEEPS_AHEAD = "keepsSeriesAhead"
        const val KEPT_SERIES = "keptSeries"
    }
}
