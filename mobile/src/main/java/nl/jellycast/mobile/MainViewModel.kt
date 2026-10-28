package nl.jellycast.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AuthState

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(val signedIn: Boolean) : MainUiState
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
            _uiState.value = MainUiState.Ready(signedIn = restored is AuthState.LoggedIn)
            sessionRepository.validateSession()
        }
    }
}
