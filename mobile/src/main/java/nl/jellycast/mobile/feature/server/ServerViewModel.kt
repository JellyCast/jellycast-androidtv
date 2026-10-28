package nl.jellycast.mobile.feature.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.ServerCheckResult
import nl.jellycast.core.model.ServerInfo

sealed interface ServerStatus {
    data object Idle : ServerStatus

    data object Checking : ServerStatus

    data class Failed(val result: ServerCheckResult) : ServerStatus

    data object Saved : ServerStatus
}

data class ServerUiState(
    val address: String = "",
    val status: ServerStatus = ServerStatus.Idle,
    val discovered: List<ServerInfo> = emptyList(),
    val searching: Boolean = false,
)

class ServerViewModel(private val sessionRepository: SessionRepository, appInfo: AppInfo) :
    ViewModel() {
    private val _uiState = MutableStateFlow(ServerUiState(address = appInfo.devServerUrl))
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    private var discoveryJob: Job? = null

    init {
        onSearchAgain()
    }

    fun onAddressChange(address: String) {
        _uiState.update { it.copy(address = address, status = ServerStatus.Idle) }
    }

    fun onConnect() {
        connect(_uiState.value.address)
    }

    fun onServerClick(server: ServerInfo) {
        _uiState.update { it.copy(address = server.url) }
        connect(server.url)
    }

    fun onSearchAgain() {
        discoveryJob?.cancel()
        _uiState.update { it.copy(discovered = emptyList(), searching = true) }
        discoveryJob =
            viewModelScope.launch {
                sessionRepository
                    .discoverServers()
                    .onCompletion { _uiState.update { it.copy(searching = false) } }
                    .collect { server ->
                        _uiState.update { state ->
                            if (state.discovered.any { it.url == server.url }) {
                                state
                            } else {
                                state.copy(discovered = state.discovered + server)
                            }
                        }
                    }
            }
    }

    fun onNavigated() {
        _uiState.update { it.copy(status = ServerStatus.Idle) }
    }

    private fun connect(input: String) {
        if (input.isBlank() || _uiState.value.status == ServerStatus.Checking) return
        _uiState.update { it.copy(status = ServerStatus.Checking) }
        viewModelScope.launch {
            val result = sessionRepository.checkServer(input.trim())
            val status =
                if (result is ServerCheckResult.Success) {
                    sessionRepository.saveServer(result.server)
                    ServerStatus.Saved
                } else {
                    ServerStatus.Failed(result)
                }
            _uiState.update { it.copy(status = status) }
        }
    }
}
