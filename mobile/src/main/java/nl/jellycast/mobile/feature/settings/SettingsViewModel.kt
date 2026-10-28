package nl.jellycast.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.AuthState
import nl.jellycast.mobile.core.chat.ChatSettings
import nl.jellycast.mobile.core.chat.ChatSettingsState
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.settings.RatingSource

data class LibraryCounts(val movies: Int, val series: Int, val episodes: Int, val syncedAtMs: Long)

data class SettingsUiState(
    val serverName: String = "",
    val serverUrl: String = "",
    val userName: String = "",
    val library: LibraryCounts? = null,
    val syncState: SyncState = SyncState.Idle,
    val quality: Map<NetworkKind, QualityPreference> = emptyMap(),
    val networkKind: NetworkKind = NetworkKind.Wifi,
    val ratingSource: RatingSource = RatingSource.Imdb,
    val chat: ChatSettingsState = ChatSettingsState(),
    val versionName: String = "",
    val versionCode: Int = 0,
)

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val store: LibraryStore,
    private val preferences: AppPreferences,
    connectivity: Connectivity,
    chatSettings: ChatSettings,
    appInfo: AppInfo,
) : ViewModel() {
    private val account =
        combine(sessionRepository.authState, store.snapshot, store.syncState) {
                auth,
                snapshot,
                sync,
            ->
            val server =
                when (auth) {
                    is AuthState.LoggedIn -> auth.server
                    is AuthState.LoggedOut -> auth.server
                    AuthState.NoServer -> null
                }
            SettingsUiState(
                serverName = server?.name.orEmpty(),
                serverUrl = server?.url.orEmpty(),
                userName = (auth as? AuthState.LoggedIn)?.userName.orEmpty(),
                library =
                    snapshot?.let {
                        LibraryCounts(
                            it.movies.size,
                            it.series.size,
                            it.episodes.size,
                            it.syncedAtMs,
                        )
                    },
                syncState = sync,
                versionName = appInfo.versionName,
                versionCode = appInfo.versionCode,
            )
        }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            account,
            preferences.quality,
            preferences.ratingSource,
            connectivity.networkKind,
            chatSettings.state,
        ) { base, quality, rating, network, chat ->
            base.copy(quality = quality, ratingSource = rating, networkKind = network, chat = chat)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            SettingsUiState(),
        )

    fun onRefreshLibrary() {
        if (store.syncState.value == SyncState.Syncing) return
        viewModelScope.launch { store.refresh() }
    }

    fun onQualityChange(network: NetworkKind, preference: QualityPreference) {
        viewModelScope.launch { preferences.setQuality(network, preference) }
    }

    fun onRatingSourceChange(source: RatingSource) {
        viewModelScope.launch { preferences.setRatingSource(source) }
    }

    /** The activity follows the session back to sign-in, where another server can be chosen. */
    fun onSignOut() {
        viewModelScope.launch {
            sessionRepository.logout()
            store.clear()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
