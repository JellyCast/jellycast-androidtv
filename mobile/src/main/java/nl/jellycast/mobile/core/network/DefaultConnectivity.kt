package nl.jellycast.mobile.core.network

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.jellycast.core.data.repository.ServerStatusRepository
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AuthState

/** What the default network offers, apart from Android types so the choice can be tested. */
data class NetworkTraits(val hasInternet: Boolean, val isCellular: Boolean, val isMetered: Boolean)

/** Local beats metered: a server in the device's own subnet is reached directly. */
fun networkKindOf(traits: NetworkTraits?, serverIsLocal: Boolean): NetworkKind = when {
    traits == null || !traits.hasInternet -> NetworkKind.None
    serverIsLocal -> NetworkKind.Local
    traits.isCellular || traits.isMetered -> NetworkKind.Cellular
    else -> NetworkKind.Wifi
}

/**
 * The network kind from the system's default network, as iOS `NetworkMonitor`, and whether the
 * server answers. The server is checked on every network change and when the app comes to the
 * foreground.
 */
class DefaultConnectivity(
    context: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val serverStatus: ServerStatusRepository,
    session: SessionRepository,
) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val traits = MutableStateFlow<NetworkTraits?>(null)
    private val serverUrl = MutableStateFlow<String?>(null)

    // Bumped when the device's addresses change, so the subnet check runs again.
    private val linkChanges = MutableStateFlow(0)

    private val _networkKind = MutableStateFlow(NetworkKind.Wifi)
    override val networkKind: StateFlow<NetworkKind> = _networkKind.asStateFlow()

    private val _isServerReachable = MutableStateFlow(true)
    override val isServerReachable: StateFlow<Boolean> = _isServerReachable.asStateFlow()

    override val isOffline: StateFlow<Boolean> =
        combine(networkKind, isServerReachable) { kind, reachable ->
            kind == NetworkKind.None || !reachable
        }.stateIn(scope, SharingStarted.Eagerly, false)

    private var checkJob: Job? = null
    private var currentNetwork: Network? = null

    init {
        traits.value = manager.getNetworkCapabilities(manager.activeNetwork)?.toTraits()
        scope.launch {
            session.authState
                .map { it.serverUrl() }
                .distinctUntilChanged()
                .collect { url ->
                    serverUrl.value = url
                    if (url != null) checkServerSoon()
                }
        }
        scope.launch {
            combine(traits, serverUrl, linkChanges) { traits, url, _ -> traits to url }
                .collect { (traits, url) ->
                    val isLocal =
                        url != null &&
                            traits?.hasInternet == true &&
                            withContext(io) {
                                LocalNetwork.contains(url, LocalNetwork.deviceSubnets())
                            }
                    _networkKind.value = networkKindOf(traits, isLocal)
                }
        }
        manager.registerDefaultNetworkCallback(DefaultNetworkCallback())
        (context.applicationContext as? Application)
            ?.registerActivityLifecycleCallbacks(ForegroundCallbacks())
    }

    override suspend fun checkServer(): Boolean {
        val reachable = serverStatus.isReachable()
        _isServerReachable.value = reachable
        return reachable
    }

    /** One failed check must not leave the app offline until the next network change. */
    @Synchronized
    private fun checkServerSoon() {
        checkJob?.cancel()
        checkJob =
            scope.launch {
                while (!checkServer() && traits.value?.hasInternet == true) {
                    delay(RECHECK_WHILE_UNREACHABLE_MS)
                }
            }
    }

    private inner class DefaultNetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val changed = network != currentNetwork || traits.value != capabilities.toTraits()
            currentNetwork = network
            traits.value = capabilities.toTraits()
            // Capabilities also change with the signal strength; only a real change counts.
            if (changed) checkServerSoon()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            linkChanges.value++
        }

        override fun onLost(network: Network) {
            currentNetwork = null
            traits.value = null
        }
    }

    private inner class ForegroundCallbacks : Application.ActivityLifecycleCallbacks {
        private var started = 0

        override fun onActivityStarted(activity: Activity) {
            if (started++ == 0) checkServerSoon()
        }

        override fun onActivityStopped(activity: Activity) {
            started = (started - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}

private fun AuthState.serverUrl(): String? = when (this) {
    is AuthState.LoggedIn -> server.url
    is AuthState.LoggedOut -> server.url
    AuthState.NoServer -> null
}

private fun NetworkCapabilities.toTraits() = NetworkTraits(
    hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
    isCellular = hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
    isMetered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
)

private const val RECHECK_WHILE_UNREACHABLE_MS = 15_000L
