package nl.jellycast.core.data

import nl.jellycast.core.datastore.StoredSession
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.ServerInfo
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo

fun StoredSession.toAuthState(): AuthState {
    val url = serverUrl ?: return AuthState.NoServer
    val server = ServerInfo(url = url, name = serverName ?: url, id = serverId.orEmpty())
    return if (accessToken != null && userId != null) {
        AuthState.LoggedIn(server = server, userName = userName.orEmpty())
    } else {
        AuthState.LoggedOut(server)
    }
}

fun isSupportedServerVersion(version: String): Boolean {
    val parsed = ServerVersion.fromString(version) ?: return false
    return parsed >= Jellyfin.minimumVersion
}

/** `null` when the address the server advertises is not a valid http(s) address. */
fun ServerDiscoveryInfo.toServerInfo(): ServerInfo? {
    val url = normalizeServerAddress(address) ?: return null
    return ServerInfo(url = url, name = name.ifBlank { url })
}
