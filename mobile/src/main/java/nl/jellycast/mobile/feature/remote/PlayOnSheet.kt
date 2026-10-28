package nl.jellycast.mobile.feature.remote

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.SheetTitle
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.remote.RemoteDevice
import org.koin.compose.viewmodel.koinViewModel

/** What the sheet plays and, for a hand-over, from where. */
data class PlayOnTarget(
    val item: PlayableItem,
    val handOffPositionMs: Long? = null,
    val handOffStreams: StreamChoice? = null,
    /** The TV that plays it now. */
    val excludedDeviceId: String? = null,
)

/**
 * Where to play: the TVs that take commands, and this phone. One tap starts it. With a
 * hand-off position it continues what plays now from there: from the player (no "This phone"),
 * or from a TV (not that TV itself). [onPlayHere] `null` hides "This phone".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayOnSheetHost(
    target: PlayOnTarget,
    onDismiss: () -> Unit,
    onPlayHere: (() -> Unit)?,
    onHandOff: () -> Unit,
    viewModel: PlayOnViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pairing by remember { mutableStateOf<RemoteDevice?>(null) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) { viewModel.onOpen() }
    val startMs = target.handOffPositionMs ?: target.item.resumeMs()
    val streams = target.handOffStreams
    val devices = state.devices.filter { it.deviceId != target.excludedDeviceId }
    val wakeable = state.wakeableTvs.filter { it.deviceId != target.excludedDeviceId }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = JellyCastColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetTitle(
                title =
                    stringResource(
                        if (target.handOffPositionMs == null) {
                            R.string.remote_play_on_title
                        } else {
                            R.string.remote_continue_on_title
                        },
                        target.item.title(),
                    ),
                subtitle =
                    startMs.takeIf { it > 0 }
                        ?.let { stringResource(R.string.remote_continues_from, clockText(it)) },
            )
            devices.forEach { device ->
                DeviceMenuRow(
                    title = device.name,
                    detail =
                        stringResource(
                            if (device.isPlaying) {
                                R.string.remote_playing_else
                            } else {
                                R.string.remote_jellycast_open
                            },
                        ),
                    icon = R.drawable.ic_tv,
                    isPaired = device.deviceId in state.pairedDeviceIds,
                    onClick = {
                        onDismiss()
                        onHandOff()
                        viewModel.onPlay(device, target.item.id, startMs, streams)
                    },
                    onRename = { renaming = device.deviceId to device.name },
                    onForget = { viewModel.onForget(device.deviceId) },
                )
            }
            wakeable.forEach { tv ->
                DeviceMenuRow(
                    title = tv.name,
                    detail = stringResource(R.string.remote_asleep),
                    icon = R.drawable.ic_tv,
                    isPaired = true,
                    onClick = {
                        onDismiss()
                        onHandOff()
                        viewModel.onWake(tv, target.item.id, startMs, streams)
                    },
                    onRename = { renaming = tv.deviceId to tv.name },
                    onForget = { viewModel.onForget(tv.deviceId) },
                )
            }
            if (onPlayHere != null) {
                PlayRow(
                    title = stringResource(R.string.remote_this_phone),
                    detail = null,
                    icon = R.drawable.ic_remote_smartphone,
                    iconTint = JellyCastColors.Text,
                    onClick = {
                        onDismiss()
                        onPlayHere()
                    },
                )
            }
            SheetNotes(
                state = state,
                isEmpty = devices.isEmpty() && wakeable.isEmpty(),
                onPair = { pairing = it },
            )
        }
    }

    pairing?.let { device ->
        PairTvSheet(device, onDismiss = { pairing = null })
    }
    renaming?.let { (deviceId, name) ->
        RenameDialog(
            initialName = name,
            onSave = {
                viewModel.onRename(deviceId, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun SheetNotes(
    state: PlayOnUiState,
    isEmpty: Boolean,
    onPair: (RemoteDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.hasUnreachablePairedTv) {
            Note(stringResource(R.string.remote_home_network_only))
        }
        if (isEmpty) {
            Text(
                stringResource(R.string.remote_no_tv),
                style = JellyCastText.Subheadline,
                color = JellyCastColors.Text,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Note(stringResource(R.string.remote_tv_missing))
        state.pairableTv?.let { device ->
            TextButton(onClick = { onPair(device) }) {
                Text(
                    stringResource(R.string.remote_pair_chromecast),
                    style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = JellyCastColors.Accent,
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = JellyCastText.Footnote,
        color = JellyCastColors.TextMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** A device row; holding it offers Rename and, for a paired TV, Forget pairing. */
@Composable
private fun DeviceMenuRow(
    title: String,
    detail: String,
    @DrawableRes icon: Int,
    isPaired: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onForget: () -> Unit,
) {
    var showsMenu by remember { mutableStateOf(false) }
    Box {
        PlayRow(
            title = title,
            detail = detail,
            icon = icon,
            iconTint = JellyCastColors.Accent,
            onClick = onClick,
            onLongClick = { showsMenu = true },
        )
        DropdownMenu(expanded = showsMenu, onDismissRequest = { showsMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remote_rename)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_remote_edit), null) },
                onClick = {
                    showsMenu = false
                    onRename()
                },
            )
            if (isPaired) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remote_forget_pairing)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_remote_link_off), null) },
                    onClick = {
                        showsMenu = false
                        onForget()
                    },
                )
            }
        }
    }
}

private val RowShape = RoundedCornerShape(18.dp)

/** The sheet row with a play icon at the end; long-pressable, which the shared row isn't. */
@Composable
private fun PlayRow(
    title: String,
    detail: String?,
    @DrawableRes icon: Int,
    iconTint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RowShape)
            .background(JellyCastColors.Text.copy(alpha = 0.05f))
            .border(BorderStroke(1.5.dp, JellyCastColors.Text.copy(alpha = 0.10f)), RowShape)
            .pressable(onClick, onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = iconTint)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = JellyCastText.Body.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(painterResource(R.drawable.ic_play), null, Modifier.size(16.dp), JellyCastColors.Text)
    }
}

@Composable
private fun RenameDialog(initialName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.remote_name_this_phone))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.remote_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.remote_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.remote_cancel)) }
        },
    )
}

private fun PlayableItem.title(): String = when (this) {
    is PlayableItem.MovieItem -> movie.title
    is PlayableItem.EpisodeItem -> episode.title
}

/** A finished item starts over, like on the TV itself. */
internal fun PlayableItem.resumeMs(): Long = when (this) {
    is PlayableItem.MovieItem -> if (movie.isPlayed) 0 else movie.resumePositionMs
    is PlayableItem.EpisodeItem -> if (episode.isPlayed) 0 else episode.resumePositionMs
}
