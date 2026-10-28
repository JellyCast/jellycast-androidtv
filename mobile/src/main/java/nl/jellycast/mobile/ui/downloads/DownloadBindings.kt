package nl.jellycast.mobile.ui.downloads

import java.io.File

/** Whether [itemId] can play without the server; everything else is dimmed while offline. */
fun interface OfflineAvailability {
    fun isAvailableOffline(itemId: String): Boolean
}

/**
 * The finished download of an item, for the player to play instead of a stream. A Compact
 * download is an HLS playlist (`.m3u8`) over a local fMP4 file, an original the file itself.
 */
fun interface LocalDownloads {
    fun localFile(itemId: String): File?
}

/**
 * A download plays without playback reports; the player records its position here every 10 s,
 * on pause and on stop, and it is sent to the server once it answers.
 */
fun interface OfflinePositions {
    fun record(itemId: String, positionMs: Long, finished: Boolean)
}
