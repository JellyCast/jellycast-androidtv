package nl.jellycast.tv.feature.server

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
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.ServerCheckResult
import nl.jellycast.core.model.ServerInfo

/** More cards do not fit in the column. */
private const val MAX_DISCOVERED_SERVERS = 3

class ServerViewModel(
    private val sessionRepository: SessionRepository,
    private val appInfo: AppInfo,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    private var discoveryJob: Job? = null

    private var currentServerUrl: String? = null

    init {
        viewModelScope.launch {
            currentServerUrl =
                when (val auth = sessionRepository.authState.first()) {
                    is AuthState.LoggedOut -> auth.server.url
                    is AuthState.LoggedIn -> auth.server.url
                    AuthState.NoServer -> null
                }
            val prefill = currentServerUrl ?: appInfo.devServerUrl
            _uiState.update { if (it.address.isEmpty()) it.copy(address = prefill) else it }
            discover()
        }
    }

    fun onManualEntryClick() {
        if (_uiState.value.status == ServerStatus.Checking) return
        _uiState.update { it.copy(manualEntry = true, status = ServerStatus.Idle) }
    }

    fun onManualEntryClose() {
        if (_uiState.value.status == ServerStatus.Checking) return
        _uiState.update { it.copy(manualEntry = false, status = ServerStatus.Idle) }
    }

    fun onSearchAgain() {
        if (discoveryJob?.isActive == true) return
        discover()
    }

    fun onServerClick(server: ServerInfo) {
        if (_uiState.value.status == ServerStatus.Checking) return
        _uiState.update { it.copy(address = server.url, status = ServerStatus.Idle) }
        onConnect()
    }

    fun onAddressChange(address: String) {
        _uiState.update { it.copy(address = address, status = ServerStatus.Idle) }
    }

    fun onConnect() {
        if (_uiState.value.status == ServerStatus.Checking) return
        _uiState.update { it.copy(status = ServerStatus.Checking) }

        viewModelScope.launch {
            when (val result = sessionRepository.checkServer(_uiState.value.address)) {
                is ServerCheckResult.Success -> {
                    sessionRepository.saveServer(result.server)
                    _uiState.update { it.copy(status = ServerStatus.Idle, serverSaved = true) }
                }

                ServerCheckResult.InvalidAddress -> setStatus(ServerStatus.InvalidAddress)

                ServerCheckResult.Unreachable -> setStatus(ServerStatus.Unreachable)

                ServerCheckResult.NotJellyfin -> setStatus(ServerStatus.NotJellyfin)

                is ServerCheckResult.UnsupportedVersion -> setStatus(
                    ServerStatus.UnsupportedVersion(result.version),
                )
            }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(serverSaved = false) }
    }

    private fun discover() {
        _uiState.update {
            it.copy(discovery = DiscoveryStatus.Searching, discoveredServers = emptyList())
        }
        discoveryJob = viewModelScope.launch {
            sessionRepository.discoverServers().collect { server ->
                val current = _uiState.value.discoveredServers
                val known = current.any { it.server.url == server.url }
                if (known || current.size >= MAX_DISCOVERED_SERVERS) return@collect
                val found = DiscoveredServer(
                    server = server,
                    isCurrent =
                        server.url == currentServerUrl,
                )
                _uiState.update { it.copy(discoveredServers = it.discoveredServers + found) }
                // The discovery broadcast reply has no version, so check each server separately.
                launch { updateCard(server.url, sessionRepository.checkServer(server.url)) }
            }
            _uiState.update { it.copy(discovery = DiscoveryStatus.Done) }
        }
    }

    private fun updateCard(url: String, result: ServerCheckResult) {
        val check =
            when (result) {
                is ServerCheckResult.Success -> ServerCardStatus.Ready(result.version)
                is ServerCheckResult.UnsupportedVersion -> ServerCardStatus.TooOld(result.version)
                else -> ServerCardStatus.Unreachable
            }
        _uiState.update { state ->
            state.copy(
                discoveredServers =
                    state.discoveredServers.map {
                        if (it.server.url == url) it.copy(check = check) else it
                    },
            )
        }
    }

    private fun setStatus(status: ServerStatus) {
        _uiState.update { it.copy(status = status) }
    }
}
