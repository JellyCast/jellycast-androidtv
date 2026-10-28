package nl.jellycast.mobile.feature.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.progress
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.AccentButton
import nl.jellycast.mobile.core.designsystem.component.ArtworkImage
import nl.jellycast.mobile.core.designsystem.component.WatchProgressBar
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.remainingText
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.PlayRequest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One list in its order: Continue plays from the first item not fully watched and goes on item
 * after item; drag a row by its handle to move it, swipe it away to remove it.
 */
@Composable
fun ListScreen(
    listId: String,
    modifier: Modifier = Modifier,
    viewModel: ListViewModel = koinViewModel(key = listId) { parametersOf(listId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    ListContent(
        state = state,
        actions =
            ListActions(
                onBack = { navigation.goBack() },
                onPlay = { navigation.play(it) },
                onMove = viewModel::onMove,
                onRemove = viewModel::onRemove,
                onRename = viewModel::onRename,
                onDelete = {
                    viewModel.onDelete()
                    navigation.goBack()
                },
            ),
        modifier = modifier,
    )
}

class ListActions(
    val onBack: () -> Unit,
    val onPlay: (PlayRequest) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onRemove: (String) -> Unit,
    val onRename: (String) -> Unit,
    val onDelete: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListContent(state: ListUiState, actions: ListActions, modifier: Modifier = Modifier) {
    var isRenaming by rememberSaveable { mutableStateOf(false) }
    var isConfirmingDelete by rememberSaveable { mutableStateOf(false) }
    val ready = state as? ListUiState.Ready
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = JellyCastColors.Background,
        contentWindowInsets = NoInsets,
        topBar = {
            ListsTopBar(ready?.name.orEmpty(), actions.onBack, scrollBehavior) {
                if (ready != null) {
                    ListMenu(
                        canPlay = ready.rows.isNotEmpty(),
                        onPlayFromStart = {
                            ready.progress
                                .playRequest(ready.listId, ready.name, 0, fromStart = true)
                                ?.let(actions.onPlay)
                        },
                        onRename = { isRenaming = true },
                        onDelete = { isConfirmingDelete = true },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ListUiState.Loading ->
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = JellyCastColors.TextMuted,
                    )

                ListUiState.Gone ->
                    Text(
                        stringResource(R.string.lists_gone),
                        color = JellyCastColors.TextMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is ListUiState.Ready -> ListItems(state, actions)
            }
        }
    }
    if (isRenaming && ready != null) {
        ListNameDialog(
            title = stringResource(R.string.lists_rename_title),
            confirmTitle = stringResource(R.string.lists_save),
            initialName = ready.name,
            onConfirm = actions.onRename,
            onDismiss = { isRenaming = false },
        )
    }
    if (isConfirmingDelete) {
        DeleteListDialog(
            onConfirm = {
                isConfirmingDelete = false
                actions.onDelete()
            },
            onDismiss = { isConfirmingDelete = false },
        )
    }
}

@Composable
private fun ListMenu(
    canPlay: Boolean,
    onPlayFromStart: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_lists_more), stringResource(R.string.lists_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canPlay) {
                MenuItem(R.string.lists_play_from_start, R.drawable.ic_library_replay) {
                    expanded = false
                    onPlayFromStart()
                }
            }
            MenuItem(R.string.lists_rename, R.drawable.ic_lists_edit) {
                expanded = false
                onRename()
            }
            MenuItem(R.string.lists_delete, R.drawable.ic_lists_delete) {
                expanded = false
                onDelete()
            }
        }
    }
}

@Composable
private fun MenuItem(title: Int, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(title)) },
        leadingIcon = { Icon(painterResource(icon), null) },
        onClick = onClick,
    )
}

@Composable
private fun DeleteListDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lists_delete_title)) },
        text = { Text(stringResource(R.string.lists_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.lists_delete), color = JellyCastColors.Warning)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.lists_cancel)) }
        },
        containerColor = JellyCastColors.Surface,
    )
}

@Composable
private fun ListItems(state: ListUiState.Ready, actions: ListActions) {
    val margin = LocalLayout.current.margin
    val listState = rememberLazyListState()
    val reorder = rememberReorderState<ListRowItem>(listState) { it.entryId }
    LaunchedEffect(state.rows) { reorder.sync(state.rows) }
    val rows = reorder.items.ifEmpty { state.rows }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            ListHeader(
                state,
                onPlay = actions.onPlay,
                modifier = Modifier.padding(
                    start = margin,
                    end = margin,
                    top = 4.dp,
                    bottom = 12.dp,
                ),
            )
        }
        itemsIndexed(rows, key = { _, row -> row.entryId }) { index, row ->
            val number = index + 1
            SwipeToDelete(onDelete = { actions.onRemove(row.entryId) }) {
                ListItemRow(
                    number = number,
                    row = row,
                    handleModifier =
                        Modifier.reorderHandle(reorder, row.entryId) { from, to ->
                            actions.onMove(from, to)
                        },
                    modifier =
                        Modifier
                            .reorderable(reorder, row.entryId)
                            .background(JellyCastColors.Background)
                            .pressable({
                                val position = state.rows.indexOfFirst { it.entryId == row.entryId }
                                state.progress
                                    .playRequest(state.listId, state.name, position)
                                    ?.let(actions.onPlay)
                            })
                            .padding(horizontal = margin),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(
                    JellyCastColors.Warning,
                ).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(R.drawable.ic_lists_delete),
                    stringResource(R.string.lists_remove),
                    tint = JellyCastColors.OnAccent,
                )
            }
        },
    ) { content() }
}

@Composable
private fun ListHeader(
    state: ListUiState.Ready,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = state.progress
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val parts = mutableListOf(countLine(progress.items.size, progress.watchedCount))
        if (progress.next != null && progress.remainingMs > 0) {
            parts += stringResource(R.string.lists_to_go, durationText(progress.remainingMs))
        }
        Text(
            parts.joinToString(" · "),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.TextMuted,
        )
        if (progress.items.isNotEmpty()) {
            val next = progress.next
            val title =
                when {
                    next == null -> stringResource(R.string.lists_play_from_start)

                    !progress.isStarted -> stringResource(R.string.lists_play)

                    else -> stringResource(
                        R.string.lists_continue_title,
                        listItemTitle(next.playable),
                    )
                }
            AccentButton(
                title = title,
                onClick = { progress.continueRequest(state.listId, state.name)?.let(onPlay) },
                icon = R.drawable.ic_play,
            )
        } else {
            Text(
                stringResource(R.string.lists_hint),
                style = JellyCastText.Subheadline,
                color = JellyCastColors.TextMuted,
                textAlign = TextAlign.Start,
            )
        }
    }
}

private const val WATCHED_ALPHA = 0.55f

@Composable
private fun ListItemRow(
    number: Int,
    row: ListRowItem,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val playable = row.playable
    val watched = playable.isPlayed
    Row(
        modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            number.toString(),
            style =
                JellyCastText.Subheadline.copy(
                    fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Medium,
                ),
            color = if (row.isNext) JellyCastColors.Accent else JellyCastColors.TextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(22.dp),
        )
        ArtworkImage(
            row.posterUrl,
            listItemTitle(playable),
            Modifier.size(width = 44.dp, height = 66.dp).alpha(if (watched) WATCHED_ALPHA else 1f),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                listItemTitle(playable),
                style = JellyCastText.Body.copy(fontWeight = FontWeight.SemiBold),
                color = if (watched) JellyCastColors.TextMuted else JellyCastColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            InfoLine(playable, row.isNext)
            if (row.isNext) {
                itemProgress(playable)?.let {
                    WatchProgressBar(it, Modifier.padding(top = 3.dp))
                }
            }
        }
        Box(
            handleModifier.size(width = 28.dp, height = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_lists_drag),
                null,
                Modifier.size(18.dp),
                tint = JellyCastColors.TextMuted.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun InfoLine(playable: PlayableItem, isNext: Boolean) {
    val watched = playable.isPlayed
    val kind =
        when (playable) {
            is PlayableItem.MovieItem ->
                listOfNotNull(
                    stringResource(R.string.library_type_movie),
                    playable.movie.year?.toString(),
                ).joinToString(" · ")

            is PlayableItem.EpisodeItem -> playable.episode.title
        }
    val time =
        when {
            watched -> stringResource(R.string.lists_watched_state)
            playable.resumeMs > 0 -> remainingText(playable.runtimeMs - playable.resumeMs)
            playable.runtimeMs > 0 -> durationText(playable.runtimeMs)
            else -> ""
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (watched) {
            Icon(
                painterResource(R.drawable.ic_lists_check_circle),
                null,
                Modifier.size(12.dp),
                tint = JellyCastColors.Accent,
            )
        }
        Text(
            listOf(kind, time).filter { it.isNotEmpty() }.joinToString(" · "),
            style = JellyCastText.Subheadline,
            color = if (isNext && !watched) JellyCastColors.Accent else JellyCastColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun ListContentPreview() {
    JellyCastTheme {
        ListContent(
            state = ListUiState.Ready("a", "Nolan", ListProgress(emptyList()), emptyList()),
            actions = ListActions({}, {}, { _, _ -> }, {}, {}, {}),
        )
    }
}

private fun itemProgress(playable: PlayableItem): Float? = when (playable) {
    is PlayableItem.MovieItem -> playable.movie.progress
    is PlayableItem.EpisodeItem -> playable.episode.progress
}
