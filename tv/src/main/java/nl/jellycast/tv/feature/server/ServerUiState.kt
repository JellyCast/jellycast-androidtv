package nl.jellycast.tv.feature.server

import nl.jellycast.core.model.ServerInfo

data class ServerUiState(
    val address: String = "",
    val status: ServerStatus = ServerStatus.Idle,
    val serverSaved: Boolean = false,
    val discovery: DiscoveryStatus = DiscoveryStatus.Searching,
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val manualEntry: Boolean = false,
)

data class DiscoveredServer(
    val server: ServerInfo,
    val check: ServerCardStatus = ServerCardStatus.Checking,
    val isCurrent: Boolean = false,
)

sealed interface ServerCardStatus {
    data object Checking : ServerCardStatus

    data class Ready(val version: String) : ServerCardStatus

    data class TooOld(val version: String) : ServerCardStatus

    data object Unreachable : ServerCardStatus
}

enum class DiscoveryStatus { Searching, Done }

sealed interface ServerStatus {
    data object Idle : ServerStatus

    data object Checking : ServerStatus

    data object InvalidAddress : ServerStatus

    data object Unreachable : ServerStatus

    data object NotJellyfin : ServerStatus

    data class UnsupportedVersion(val version: String) : ServerStatus
}
