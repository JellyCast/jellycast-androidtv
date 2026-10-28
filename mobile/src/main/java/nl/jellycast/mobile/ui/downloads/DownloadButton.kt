package nl.jellycast.mobile.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The download button with its five states (download, waiting, progress, downloaded, failed), as
 * on iOS. [compact] is the small variant in episode rows.
 *
 * Owner: the downloads worker. STUB.
 */
@Composable
fun DownloadButton(itemId: String, modifier: Modifier = Modifier, compact: Boolean = false) = Unit

/**
 * The bar above the tab bar while a download runs (not on the Downloads tab). Draws nothing when
 * no download runs. Returns whether it drew.
 *
 * Owner: the downloads worker. STUB.
 */
@Composable
fun DownloadAccessory(onOpenDownloads: () -> Unit, modifier: Modifier = Modifier): Boolean = false

/** Whether [itemId] can play without the server; everything else is dimmed while offline. */
fun interface OfflineAvailability {
    fun isAvailableOffline(itemId: String): Boolean
}
