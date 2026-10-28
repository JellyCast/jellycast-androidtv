package nl.jellycast.core.network

import android.content.Context
import kotlin.time.Duration.Companion.seconds
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo

// On the LAN a server answers within seconds; fail fast when checking an address.
val ServerCheckHttpOptions =
    HttpClientOptions(connectTimeout = 4.seconds, requestTimeout = 8.seconds)

// The SDK derives the device id from `ANDROID_ID`, stable across reinstalls, so the server keeps
// seeing the TV as the same device.
fun createJellyfinInstance(context: Context, appVersion: String): Jellyfin = createJellyfin {
    this.context = context
    clientInfo = ClientInfo(name = "JellyCast", version = appVersion)
}
