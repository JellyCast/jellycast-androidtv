package nl.jellycast.mobile.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.LoginResult
import nl.jellycast.core.model.QuickConnectState

enum class PasswordStatus { Idle, Busy, InvalidCredentials, Unreachable }

data class LoginUiState(
    val serverName: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val passwordStatus: PasswordStatus = PasswordStatus.Idle,
    /** `null` until the user asks for a code: on a phone the password is the usual way. */
    val quickConnect: QuickConnectState? = null,
)

class LoginViewModel(private val sessionRepository: SessionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var quickConnectJob: Job? = null

    init {
        viewModelScope.launch {
            val server =
                when (val auth = sessionRepository.authState.first()) {
                    is AuthState.LoggedOut -> auth.server
                    is AuthState.LoggedIn -> auth.server
                    AuthState.NoServer -> null
                }
            if (server != null) {
                _uiState.update { it.copy(serverName = server.name, serverUrl = server.url) }
            }
        }
    }

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, passwordStatus = PasswordStatus.Idle) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordStatus = PasswordStatus.Idle) }
    }

    fun onPasswordLogin() {
        val state = _uiState.value
        if (state.passwordStatus == PasswordStatus.Busy || state.username.isBlank()) return
        _uiState.update { it.copy(passwordStatus = PasswordStatus.Busy) }
        viewModelScope.launch {
            val status =
                when (sessionRepository.loginWithPassword(state.username.trim(), state.password)) {
                    LoginResult.Success -> PasswordStatus.Idle
                    LoginResult.InvalidCredentials -> PasswordStatus.InvalidCredentials
                    LoginResult.Unreachable -> PasswordStatus.Unreachable
                }
            _uiState.update { it.copy(passwordStatus = status) }
        }
    }

    fun onStartQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob =
            viewModelScope.launch {
                sessionRepository.quickConnect().collect { quickConnect ->
                    _uiState.update { it.copy(quickConnect = quickConnect) }
                }
            }
    }

    fun onCancelQuickConnect() {
        quickConnectJob?.cancel()
        _uiState.update { it.copy(quickConnect = null) }
    }
}
