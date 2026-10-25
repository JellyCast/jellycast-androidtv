package nl.jellycast.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.tv.core.data.repository.LanguageRepository
import nl.jellycast.tv.core.data.repository.SessionRepository
import nl.jellycast.tv.core.data.repository.UiPreferencesRepository
import nl.jellycast.tv.core.model.AppInfo
import nl.jellycast.tv.core.model.AuthState
import nl.jellycast.tv.core.model.MovieView
import nl.jellycast.tv.core.playback.PlaybackSettingsStore
import nl.jellycast.tv.core.playback.nextMaxBitrate

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    appInfo: AppInfo,
    private val playbackSettings: PlaybackSettingsStore,
    private val uiPreferences: UiPreferencesRepository,
    private val languageRepository: LanguageRepository,
) : ViewModel() {
    private val isLoggingOut = MutableStateFlow(false)

    // The language button takes initial focus, so it must exist in the very first state.
    private val initialState =
        SettingsUiState(
            versionName = appInfo.versionName,
            versionCode = appInfo.versionCode,
            language = languageRepository.current.value,
        )

    val uiState: StateFlow<SettingsUiState> =
        combine(
            sessionRepository.authState,
            isLoggingOut,
            playbackSettings.settings,
            uiPreferences.getPreferencesStream(),
        ) { auth, loggingOut, settings, preferences ->
            val loggedIn = auth as? AuthState.LoggedIn
            initialState.copy(
                serverName = loggedIn?.server?.name.orEmpty(),
                userName = loggedIn?.userName.orEmpty(),
                isLoggingOut = loggingOut,
                burnInAss = settings.burnInAss,
                maxBitrateMbps = settings.maxStreamingBitrate.toMbpsOrNull(),
                movieView = preferences.movieView,
            )
        }.combine(languageRepository.current) { state, language ->
            state.copy(language = language)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState)

    fun onBurnInAssToggle() {
        playbackSettings.setBurnInAss(!playbackSettings.settings.value.burnInAss)
    }

    fun onMovieViewToggle() {
        val next =
            when (uiState.value.movieView) {
                MovieView.Detail -> MovieView.Bookcase
                MovieView.Bookcase -> MovieView.Detail
            }
        viewModelScope.launch { uiPreferences.setMovieView(next) }
    }

    fun onLanguageClick() {
        val languages = languageRepository.languages
        if (languages.size < 2) return
        val index = languages.indexOf(languageRepository.current.value)
        languageRepository.setLanguage(languages[(index + 1) % languages.size])
    }

    fun onMaxBitrateClick() {
        playbackSettings.setMaxStreamingBitrate(
            nextMaxBitrate(playbackSettings.settings.value.maxStreamingBitrate),
        )
    }

    fun onLogout() {
        if (isLoggingOut.value) return
        isLoggingOut.value = true
        viewModelScope.launch { sessionRepository.logout() }
    }
}
