package nl.jellycast.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.AdRepository
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.data.repository.UsageRepository
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.UsageChoice

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(val signedIn: Boolean) : MainUiState
}

class MainViewModel(
    private val sessionRepository: SessionRepository,
    private val usage: UsageRepository,
    private val ads: AdRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val authState: StateFlow<AuthState?> =
        sessionRepository.authState.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    /** `null` until known; `true` while the usage question is unanswered. */
    val needsUsageChoice: StateFlow<Boolean?> =
        usage.choice
            .map { it == null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onUsageChoice(choice: UsageChoice) {
        viewModelScope.launch { usage.choose(choice) }
    }

    /** At launch and every time the app comes back to the foreground. */
    fun onAppVisible() {
        viewModelScope.launch { usage.appOpened() }
        viewModelScope.launch { ads.refresh() }
    }

    init {
        viewModelScope.launch {
            val restored = sessionRepository.restore()
            _uiState.value = MainUiState.Ready(signedIn = restored is AuthState.LoggedIn)
            sessionRepository.validateSession()
        }
    }
}
