package nl.jellycast.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.AuthState

data class SettingsUiState(
    val userName: String = "",
    val serverName: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
)

class SettingsViewModel(private val sessionRepository: SessionRepository, appInfo: AppInfo) :
    ViewModel() {
    private val initial =
        SettingsUiState(versionName = appInfo.versionName, versionCode = appInfo.versionCode)

    val uiState: StateFlow<SettingsUiState> =
        sessionRepository.authState
            .map { auth ->
                when (auth) {
                    is AuthState.LoggedIn ->
                        initial.copy(userName = auth.userName, serverName = auth.server.name)

                    is AuthState.LoggedOut -> initial.copy(serverName = auth.server.name)

                    AuthState.NoServer -> initial
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    fun onSignOut() {
        viewModelScope.launch { sessionRepository.logout() }
    }
}
