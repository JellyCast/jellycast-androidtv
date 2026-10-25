package nl.jellycast.tv.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.tv.core.data.repository.SessionRepository
import nl.jellycast.tv.core.model.AuthState
import nl.jellycast.tv.core.model.LoginResult
import nl.jellycast.tv.core.model.QuickConnectState

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
                _uiState.update {
                    it.copy(serverName = server.name, serverHost = server.url.toDisplayHost())
                }
            }
        }
        startQuickConnect()
    }

    fun onRetryQuickConnect() {
        startQuickConnect()
    }

    fun onUsePassword() {
        _uiState.update { it.copy(mode = LoginMode.Password) }
    }

    fun onUseQuickConnect() {
        _uiState.update {
            it.copy(
                mode = LoginMode.QuickConnect,
                password = "",
                passwordStatus = PasswordStatus.Idle,
            )
        }
        val hasCode = _uiState.value.quickConnect is QuickConnectState.WaitingForApproval
        if (!hasCode) startQuickConnect()
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

    private fun startQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob =
            viewModelScope.launch {
                sessionRepository.quickConnect().collect { quickConnect ->
                    _uiState.update {
                        val mode =
                            if (quickConnect == QuickConnectState.Disabled) {
                                LoginMode.Password
                            } else {
                                it.mode
                            }
                        it.copy(quickConnect = quickConnect, mode = mode)
                    }
                }
            }
    }
}

/** "http://192.168.1.4:8096" → "192.168.1.4"; without a recognizable host the input as is. */
internal fun String.toDisplayHost(): String = try {
    URI(this).host ?: this
} catch (_: URISyntaxException) {
    this
}
