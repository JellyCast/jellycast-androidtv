package nl.jellycast.mobile.testing

import kotlinx.coroutines.flow.MutableStateFlow
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.ui.downloads.OfflineAvailability

/** Connectivity for Home tests: flip [isOffline] directly. */
class HomeFakeConnectivity(offline: Boolean = false) : Connectivity {
    override val networkKind = MutableStateFlow(if (offline) NetworkKind.None else NetworkKind.Wifi)
    override val isServerReachable = MutableStateFlow(!offline)
    override val isOffline = MutableStateFlow(offline)

    override suspend fun checkServer(): Boolean = isServerReachable.value
}

/** Only [downloadedIds] play offline. */
class HomeFakeOfflineAvailability(private vararg val downloadedIds: String) : OfflineAvailability {
    override fun isAvailableOffline(itemId: String): Boolean = itemId in downloadedIds
}
