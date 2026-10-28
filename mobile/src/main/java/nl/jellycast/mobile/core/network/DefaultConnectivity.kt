package nl.jellycast.mobile.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Owner: the library worker. A first version: Wi-Fi or cellular from the system, without the
 * "server in the local subnet" check (Local) and without the server ping.
 */
class DefaultConnectivity(context: Context, scope: CoroutineScope) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _networkKind = MutableStateFlow(currentKind())
    override val networkKind: StateFlow<NetworkKind> = _networkKind.asStateFlow()

    private val _isServerReachable = MutableStateFlow(true)
    override val isServerReachable: StateFlow<Boolean> = _isServerReachable.asStateFlow()

    override val isOffline: StateFlow<Boolean> =
        combine(networkKind, isServerReachable) { kind, reachable ->
            kind == NetworkKind.None ||
                !reachable
        }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    _networkKind.value = capabilities.toKind()
                }

                override fun onLost(network: Network) {
                    _networkKind.value = NetworkKind.None
                }
            },
        )
    }

    override suspend fun checkServer(): Boolean = _isServerReachable.value

    private fun currentKind(): NetworkKind =
        manager.getNetworkCapabilities(manager.activeNetwork)?.toKind() ?: NetworkKind.None
}

private fun NetworkCapabilities.toKind(): NetworkKind = when {
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Cellular
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkKind.Wifi
    else -> NetworkKind.None
}
