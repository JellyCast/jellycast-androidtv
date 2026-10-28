package nl.jellycast.mobile.ui.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.downloads.Download
import nl.jellycast.mobile.core.downloads.DownloadStatus
import nl.jellycast.mobile.core.downloads.DownloadStore
import nl.jellycast.mobile.core.library.LibraryStore
import org.koin.compose.koinInject

/**
 * The round download button of a movie or episode, as on iOS. A movie asks first in the
 * download sheet; an episode starts at once with the default choice, and a long press opens
 * the sheet. With a download a tap opens its menu. [compact] is the small variant.
 */
@Composable
fun DownloadButton(itemId: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val store: DownloadStore = koinInject()
    val library: LibraryStore = koinInject()
    val downloads by store.downloads.collectAsStateWithLifecycle()
    val reachable by store.canReachServer.collectAsStateWithLifecycle()
    val snapshot by library.snapshot.collectAsStateWithLifecycle()
    val download = downloads.firstOrNull { it.id == itemId }
    val item: PlayableItem? =
        snapshot?.moviesById?.get(itemId)?.let { PlayableItem.MovieItem(it) }
            ?: snapshot?.episodesById?.get(itemId)?.let { PlayableItem.EpisodeItem(it) }
            ?: download?.media?.toPlayable()
    val text = rememberDownloadText()
    val scope = rememberCoroutineScope()
    val requestNotifications = rememberNotificationPermissionRequest()
    var showsSheet by remember { mutableStateOf(false) }
    var showsMenu by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val asksFirst = item is PlayableItem.MovieItem
    val onlyAtHome = stringResource(R.string.downloads_only_at_home)

    fun tapped(item: PlayableItem) = scope.launch {
        if (!store.canReachServer.value) {
            isPreparing = true
            store.recheckServer()
            isPreparing = false
        }
        if (!store.canReachServer.value) {
            message = onlyAtHome
            return@launch
        }
        if (asksFirst) {
            showsSheet = true
            return@launch
        }
        isPreparing = true
        val plan = store.plan(item).plan
        isPreparing = false
        val kind = plan.defaultKind
        val bytes = plan.bytes(kind)
        val free = store.freeBytes()
        if (bytes != null && free != null && bytes > free) {
            message = text.doesNotFit(bytes - free)
            return@launch
        }
        requestNotifications()
        store.enqueue(item, kind, bytes)
    }

    val label =
        download?.let { text.statusLine(it, null) } ?: stringResource(R.string.downloads_download)
    Box(modifier) {
        DownloadStateIcon(
            download = download,
            size = if (compact) CompactSize else RegularSize,
            isPreparing = isPreparing,
            modifier =
                Modifier
                    .alpha(if (download == null && !reachable) AWAY_ALPHA else 1f)
                    .semantics { contentDescription = label }
                    .pressable(
                        onClick = {
                            when {
                                download != null -> showsMenu = true
                                item != null && !isPreparing -> tapped(item)
                            }
                        },
                        onLongClick =
                            if (download == null && item != null && !asksFirst && reachable) {
                                { showsSheet = true }
                            } else {
                                null
                            },
                    ),
        )
        if (download != null) {
            DownloadMenu(
                download = download,
                expanded = showsMenu,
                onDismiss = { showsMenu = false },
                store = store,
            )
        }
    }
    if (showsSheet && item != null) {
        DownloadSheet(
            item = item,
            onDismiss = { showsSheet = false },
            onDownload = { requestNotifications() },
        )
    }
    message?.let { DownloadMessage(it, onDismiss = { message = null }) }
}

@Composable
private fun DownloadMenu(
    download: Download,
    expanded: Boolean,
    onDismiss: () -> Unit,
    store: DownloadStore,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        fun act(action: () -> Unit) {
            onDismiss()
            action()
        }
        when (download.status) {
            DownloadStatus.Done ->
                MenuItem(R.string.downloads_remove_download, R.drawable.ic_downloads_delete) {
                    act { store.remove(download.id) }
                }

            DownloadStatus.Paused, is DownloadStatus.Failed -> {
                MenuItem(R.string.downloads_go_on, R.drawable.ic_downloads_arrow_down) {
                    act { store.resume(download.id) }
                }
                MenuItem(R.string.downloads_cancel_download, R.drawable.ic_downloads_close) {
                    act { store.remove(download.id) }
                }
            }

            else -> {
                MenuItem(R.string.downloads_pause, R.drawable.ic_downloads_pause) {
                    act { store.pause(download.id) }
                }
                MenuItem(R.string.downloads_cancel_download, R.drawable.ic_downloads_close) {
                    act { store.remove(download.id) }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(title: Int, @DrawableRes icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(title)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
internal fun DownloadMessage(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.downloads_ok), color = JellyCastColors.Accent)
            }
        },
        text = { Text(message) },
        containerColor = JellyCastColors.Surface,
    )
}

/** Asks once for the notification that shows a running download; downloading works without. */
@Composable
internal fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(context, launcher) {
        {
            // Before Android 13 notifications need no permission.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted =
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private val RegularSize = 34.dp
private val CompactSize = 30.dp
private const val AWAY_ALPHA = 0.4f
