package nl.jellycast.mobile.core.network

import kotlinx.coroutines.flow.StateFlow

enum class NetworkKind { Local, Wifi, Cellular, None }

/**
 * [isOffline] is true without a network or while the server doesn't answer. Items that aren't
 * downloaded are dimmed and can't be tapped while it holds.
 */
interface Connectivity {
    val networkKind: StateFlow<NetworkKind>

    val isServerReachable: StateFlow<Boolean>

    val isOffline: StateFlow<Boolean>

    /** `GET /System/Info/Public` with a short timeout; updates [isServerReachable]. */
    suspend fun checkServer(): Boolean
}
