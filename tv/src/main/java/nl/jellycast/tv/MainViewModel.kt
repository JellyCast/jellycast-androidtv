package nl.jellycast.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AuthState
import nl.jellycast.tv.navigation.Home
import nl.jellycast.tv.navigation.Login
import nl.jellycast.tv.navigation.ServerSetup

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(val startDestination: NavKey) : MainUiState
}

class MainViewModel(private val sessionRepository: SessionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val authState: StateFlow<AuthState?> =
        sessionRepository.authState.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    init {
        viewModelScope.launch {
            val restored = sessionRepository.restore()
            _uiState.value = MainUiState.Ready(restored.startDestination())
            // After the start screen shows; a revoked token then redirects to Login via authState.
            sessionRepository.validateSession()
        }
    }
}

fun AuthState.startDestination(): NavKey = when (this) {
    AuthState.NoServer -> ServerSetup
    is AuthState.LoggedOut -> Login
    is AuthState.LoggedIn -> Home
}
