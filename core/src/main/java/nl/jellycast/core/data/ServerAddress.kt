package nl.jellycast.core.data

import java.net.URI
import java.net.URISyntaxException

private const val DEFAULT_HTTP_PORT = 8096

/**
 * Turns typed input into a Jellyfin API base URL, or `null` if it is not a valid address.
 *
 * - `192.168.1.4` → `http://192.168.1.4:8096` (Jellyfin's default port for http without a port)
 * - `https://jf.example.nl/` → `https://jf.example.nl`
 * - a URL pasted from the browser (`…/web/#/home`) → only the server base
 */
fun normalizeServerAddress(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null

    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val uri =
        try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return null
        }

    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null

    val port =
        when {
            uri.port != -1 -> uri.port
            scheme == "http" -> DEFAULT_HTTP_PORT
            else -> -1
        }
    val path =
        uri.path
            .orEmpty()
            .removeSuffix("/")
            .removeSuffix("/web/index.html")
            .removeSuffix("/web")
            .removeSuffix("/")

    return buildString {
        append(scheme).append("://").append(host)
        if (port != -1) append(':').append(port)
        append(path)
    }
}
