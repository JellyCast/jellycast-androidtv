package nl.jellycast.mobile.feature.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.data.normalizeServerAddress
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.QuickConnectState
import nl.jellycast.core.model.ServerCheckResult
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind

sealed interface SignInError {
    data object InvalidAddress : SignInError

    data object NotJellyfin : SignInError

    data object Unreachable : SignInError

    data object Offline : SignInError

    data class UnsupportedVersion(val version: String) : SignInError

    data object QuickConnectDisabled : SignInError

    data object QuickConnectFailed : SignInError
}

sealed interface SignInPhase {
    data object EnterAddress : SignInPhase

    data object CheckingServer : SignInPhase

    data class WaitingForApproval(val serverName: String, val code: String) : SignInPhase

    data class Failed(val error: SignInError) : SignInPhase
}

sealed interface ServerCheck {
    data object Checking : ServerCheck

    data class Ready(val version: String) : ServerCheck

    data object Unreachable : ServerCheck
}

data class FoundServer(
    val server: DiscoveredServer,
    val check: ServerCheck = ServerCheck.Checking,
    val isCurrent: Boolean = false,
)

data class SignInUiState(
    val address: String = "",
    val phase: SignInPhase = SignInPhase.EnterAddress,
    val isSearching: Boolean = true,
    val foundServers: List<FoundServer> = emptyList(),
) {
    val canConnect: Boolean get() = normalizeServerAddress(address) != null
}

/**
 * Server discovery and Quick Connect on one screen, as iOS: there is no password sign-in. Signing
 * in changes the session, and the activity follows it to the app.
 */
class SignInViewModel(
    private val sessionRepository: SessionRepository,
    private val discovery: ServerDiscovery,
    private val connectivity: Connectivity,
    appInfo: AppInfo,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUiState(address = appInfo.devServerUrl))
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private var discoveryJob: Job? = null
    private var connectJob: Job? = null
    private var currentUrl: String? = null

    init {
        viewModelScope.launch {
            currentUrl =
                when (val auth = sessionRepository.authState.first()) {
                    is AuthState.LoggedOut -> auth.server.url
                    is AuthState.LoggedIn -> auth.server.url
                    AuthState.NoServer -> null
                }
            currentUrl?.let { url -> _uiState.update { it.copy(address = url) } }
            search()
        }
    }

    fun onAddressChange(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    fun onSearchAgain() {
        search()
    }

    fun onConnect() {
        val state = _uiState.value
        if (!state.canConnect || state.phase == SignInPhase.CheckingServer) return
        connect(state.address)
    }

    fun onServerClick(server: DiscoveredServer) {
        if (_uiState.value.phase == SignInPhase.CheckingServer) return
        _uiState.update { it.copy(address = server.url) }
        connect(server.url)
    }

    fun onCancel() {
        connectJob?.cancel()
        _uiState.update { it.copy(phase = SignInPhase.EnterAddress) }
    }

    private fun search() {
        discoveryJob?.cancel()
        _uiState.update { it.copy(isSearching = true, foundServers = emptyList()) }
        discoveryJob =
            viewModelScope.launch {
                discovery.search().collect { server ->
                    _uiState.update { state ->
                        val found = FoundServer(server, isCurrent = server.url == currentUrl)
                        state.copy(foundServers = state.foundServers + found)
                    }
                    // The discovery reply has no version, so each server is checked separately.
                    launch { check(server) }
                }
                _uiState.update { it.copy(isSearching = false) }
            }
    }

    private suspend fun check(server: DiscoveredServer) {
        val check =
            when (val result = sessionRepository.checkServer(server.url)) {
                is ServerCheckResult.Success -> ServerCheck.Ready(result.version)
                is ServerCheckResult.UnsupportedVersion -> ServerCheck.Ready(result.version)
                else -> ServerCheck.Unreachable
            }
        _uiState.update { state ->
            state.copy(
                foundServers =
                    state.foundServers.map {
                        if (it.server.id == server.id) it.copy(check = check) else it
                    },
            )
        }
    }

    private fun connect(address: String) {
        connectJob?.cancel()
        _uiState.update { it.copy(phase = SignInPhase.CheckingServer) }
        connectJob =
            viewModelScope.launch {
                val result = sessionRepository.checkServer(address.trim())
                if (result !is ServerCheckResult.Success) {
                    fail(result.toError())
                    return@launch
                }
                sessionRepository.saveServer(result.server)
                currentUrl = result.server.url
                sessionRepository.quickConnect().collect { state ->
                    when (state) {
                        QuickConnectState.Starting, QuickConnectState.Authenticated -> Unit

                        is QuickConnectState.WaitingForApproval ->
                            _uiState.update {
                                it.copy(
                                    phase =
                                        SignInPhase.WaitingForApproval(result.server.name, state.code),
                                )
                            }

                        QuickConnectState.Disabled -> fail(SignInError.QuickConnectDisabled)

                        QuickConnectState.Failed -> fail(SignInError.QuickConnectFailed)
                    }
                }
            }
    }

    private fun fail(error: SignInError) {
        _uiState.update { it.copy(phase = SignInPhase.Failed(error)) }
    }

    private fun ServerCheckResult.toError(): SignInError = when (this) {
        ServerCheckResult.InvalidAddress -> SignInError.InvalidAddress

        ServerCheckResult.NotJellyfin -> SignInError.NotJellyfin

        is ServerCheckResult.UnsupportedVersion -> SignInError.UnsupportedVersion(version)

        ServerCheckResult.Unreachable, is ServerCheckResult.Success ->
            if (connectivity.networkKind.value == NetworkKind.None) {
                SignInError.Offline
            } else {
                SignInError.Unreachable
            }
    }
}
