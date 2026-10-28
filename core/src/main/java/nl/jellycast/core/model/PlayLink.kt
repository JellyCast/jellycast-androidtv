package nl.jellycast.core.model

import java.net.URI
import java.net.URISyntaxException

/**
 * `jellycast://play?item=<id>&start=<ms>&audio=<index>&subtitle=<index>`: what the iOS app opens through the Android TV remote
 * protocol to start something while the Chromecast was asleep.
 */
fun parsePlayLink(link: String): PlayRequest? {
    val uri =
        try {
            URI(link)
        } catch (_: URISyntaxException) {
            return null
        }
    if (uri.scheme != PLAY_LINK_SCHEME || uri.host != PLAY_LINK_HOST) return null
    val query =
        uri.rawQuery
            ?.split('&')
            ?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
            ?.associate { (key, value) -> key to value }
            .orEmpty()
    val itemId = query["item"]?.takeIf { ITEM_ID.matches(it) } ?: return null
    val startMs = query["start"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
    val streams = StreamChoice(query["audio"]?.toIntOrNull(), query["subtitle"]?.toIntOrNull())
    return PlayRequest(itemId, startMs, streams)
}

fun playLink(itemId: String, startMs: Long): String =
    "$PLAY_LINK_SCHEME://$PLAY_LINK_HOST?item=$itemId&start=${startMs.coerceAtLeast(0)}"

private const val PLAY_LINK_SCHEME = "jellycast"
private const val PLAY_LINK_HOST = "play"
private val ITEM_ID = Regex("[0-9a-fA-F]{32}")
