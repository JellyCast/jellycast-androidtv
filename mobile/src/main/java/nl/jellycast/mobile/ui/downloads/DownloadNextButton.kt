package nl.jellycast.mobile.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.downloads.DownloadSettings
import nl.jellycast.mobile.core.downloads.DownloadStore
import nl.jellycast.mobile.core.downloads.DownloadedMedia
import nl.jellycast.mobile.core.downloads.EpisodePlan
import nl.jellycast.mobile.core.downloads.enqueue
import nl.jellycast.mobile.core.downloads.missingEpisodes
import nl.jellycast.mobile.core.downloads.plans
import nl.jellycast.mobile.core.downloads.upcomingEpisodes
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.library.progressOf
import org.koin.compose.koinInject

/**
 * "Download next 5" on the series page: the next unwatched episodes in order, after showing
 * what they take. The series then stays ahead when that setting is on. Invisible when nothing
 * is left to watch.
 */
@Composable
fun DownloadNextButton(seriesId: String, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val store: DownloadStore = koinInject()
    val library: LibraryStore = koinInject()
    val snapshot by library.snapshot.collectAsStateWithLifecycle()
    val downloads by store.downloads.collectAsStateWithLifecycle()
    val progress =
        remember(snapshot, seriesId) {
            snapshot?.let { library ->
                library.seriesById[seriesId]?.let {
                    progressOf(it, library.episodesBySeries[seriesId].orEmpty())
                }
            }
        } ?: return
    val upcoming = remember(progress) { upcomingEpisodes(progress, DownloadSettings.SERIES_BATCH) }
    val missing = remember(progress, downloads) { store.missingEpisodes(progress) }
    val text = rememberDownloadText()
    val scope = rememberCoroutineScope()
    val requestNotifications = rememberNotificationPermissionRequest()
    var plans by remember { mutableStateOf<List<EpisodePlan>>(emptyList()) }
    var showsConfirmation by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val onlyAtHome = stringResource(R.string.downloads_only_at_home)
    val label =
        if (missing.isEmpty()) {
            stringResource(R.string.downloads_next_on_device)
        } else {
            stringResource(R.string.downloads_download_next, missing.size)
        }

    Box(
        modifier
            .alpha(if (upcoming.isEmpty()) 0f else 1f)
            .size(size)
            .clip(CircleShape)
            .background(JellyCastColors.Rail)
            .semantics { contentDescription = label }
            .pressable(
                enabled = missing.isNotEmpty() && !isPreparing && upcoming.isNotEmpty(),
                onClick = {
                    scope.launch {
                        isPreparing = true
                        if (!store.canReachServer.value) store.recheckServer()
                        if (!store.canReachServer.value) {
                            isPreparing = false
                            message = onlyAtHome
                            return@launch
                        }
                        plans = store.plans(store.missingEpisodes(progress))
                        isPreparing = false
                        if (plans.isNotEmpty()) showsConfirmation = true
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isPreparing ->
                CircularProgressIndicator(
                    Modifier.size(size * 0.45f),
                    color = JellyCastColors.Text,
                    strokeWidth = 2.dp,
                )

            missing.isEmpty() ->
                Icon(
                    painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    Modifier.size(22.dp),
                    tint = JellyCastColors.Accent,
                )

            else ->
                Icon(
                    painterResource(R.drawable.ic_downloads_to_device),
                    contentDescription = null,
                    Modifier.size(16.dp),
                    tint = JellyCastColors.Text,
                )
        }
    }

    if (showsConfirmation) {
        val free = store.freeBytes()
        val first = plans.firstOrNull()?.episode
        val last = plans.lastOrNull()?.episode
        val range =
            listOfNotNull(first, last).distinctBy { it.id }
                .mapNotNull { text.episodeCode(DownloadedMedia.of(PlayableItem.EpisodeItem(it))) }
                .joinToString(" – ")
        val total = plans.sumOf { it.bytes ?: 0 }
        val details =
            DownloadText.joined(
                range,
                stringResource(R.string.downloads_about_size, text.bytes(total)),
                free?.let { stringResource(R.string.downloads_storage_free, text.bytes(it)) },
            )
        AlertDialog(
            onDismissRequest = { showsConfirmation = false },
            title = { Text(progress.series.title) },
            text = { Text(details) },
            containerColor = JellyCastColors.Surface,
            confirmButton = {
                TextButton(
                    onClick = {
                        showsConfirmation = false
                        val queued = store.enqueue(plans)
                        store.settings.keep(seriesId)
                        requestNotifications()
                        if (queued < plans.size) message = text.onlyFit(queued, plans.size)
                    },
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.downloads_download_episodes,
                            plans.size,
                            plans.size,
                        ),
                        color = JellyCastColors.Accent,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showsConfirmation = false }) {
                    Text(stringResource(R.string.downloads_cancel), color = JellyCastColors.Text)
                }
            },
        )
    }
    message?.let { DownloadMessage(it, onDismiss = { message = null }) }
}
