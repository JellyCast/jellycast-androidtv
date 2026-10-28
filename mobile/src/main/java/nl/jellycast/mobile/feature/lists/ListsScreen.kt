package nl.jellycast.mobile.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ArtworkImage
import nl.jellycast.mobile.core.designsystem.component.ContentErrorView
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.navigation.ListRoute
import nl.jellycast.mobile.navigation.LocalAppNavigation
import org.koin.androidx.compose.koinViewModel

/** The user's lists, opened from the Library filter sheet. */
@Composable
fun ListsScreen(modifier: Modifier = Modifier, viewModel: ListsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    LaunchedEffect(state.createdListId) {
        state.createdListId?.let {
            navigation.open(ListRoute(it))
            viewModel.onNavigated()
        }
    }
    ListsContent(
        state = state,
        onBack = { navigation.goBack() },
        onOpen = { navigation.open(ListRoute(it)) },
        onCreate = viewModel::onCreate,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsContent(
    state: ListsUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isNaming by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = JellyCastColors.Background,
        contentWindowInsets = NoInsets,
        topBar = {
            ListsTopBar(stringResource(R.string.lists_title), onBack, scrollBehavior) {
                IconButton(onClick = { isNaming = true }) {
                    Icon(
                        painterResource(R.drawable.ic_lists_add),
                        stringResource(R.string.lists_new),
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val load = state.load
            when {
                state.lists.isNotEmpty() -> ListsList(state.lists, onOpen)

                load is ListsLoad.Failed ->
                    ContentErrorView(
                        message = load.message.orEmpty(),
                        retryTitle = stringResource(R.string.retry),
                        onRetry = onRefresh,
                        modifier = Modifier.align(Alignment.Center),
                    )

                load == ListsLoad.Loaded -> EmptyLists(Modifier.align(Alignment.Center))

                else ->
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = JellyCastColors.TextMuted,
                    )
            }
        }
    }
    if (isNaming) {
        ListNameDialog(
            title = stringResource(R.string.lists_new),
            confirmTitle = stringResource(R.string.lists_create),
            onConfirm = onCreate,
            onDismiss = { isNaming = false },
        )
    }
}

@Composable
private fun ListsList(lists: List<ListSummary>, onOpen: (String) -> Unit) {
    val margin = LocalLayout.current.margin
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = margin, end = margin, bottom = 24.dp),
    ) {
        item(key = "subtitle") {
            Text(
                stringResource(R.string.lists_subtitle),
                style = JellyCastText.Subheadline,
                color = JellyCastColors.TextMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(lists, key = { it.id }) { list ->
            ListSummaryRow(list, Modifier.pressable({ onOpen(list.id) }))
        }
    }
}

@Composable
private fun ListSummaryRow(list: ListSummary, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PosterStack(list.posterUrls, list.name)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                list.name,
                style = JellyCastText.Body.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                countLine(list.count, list.watchedCount),
                style = JellyCastText.Subheadline,
                color = JellyCastColors.TextMuted,
                maxLines = 1,
            )
            val (status, accented) =
                when (val status = list.status) {
                    ListStatus.Empty -> stringResource(R.string.lists_empty) to false

                    ListStatus.AllWatched -> stringResource(R.string.lists_all_watched) to false

                    ListStatus.NotStarted -> stringResource(R.string.lists_not_started) to false

                    is ListStatus.ContinueWith ->
                        stringResource(R.string.lists_continue_with, listItemTitle(status.next)) to
                            true
                }
            Text(
                status,
                style = JellyCastText.Subheadline,
                color = if (accented) JellyCastColors.Accent else JellyCastColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painterResource(R.drawable.ic_library_chevron_right),
            null,
            Modifier.width(28.dp).size(18.dp),
            tint = JellyCastColors.TextMuted,
        )
    }
}

/** "3 titles · 1 watched"; the watched part only once something is. */
@Composable
fun countLine(count: Int, watchedCount: Int): String {
    val titles = pluralStringResource(R.plurals.lists_titles, count, count)
    return if (watchedCount > 0) {
        "$titles · ${pluralStringResource(
            R.plurals.lists_watched_count,
            watchedCount,
            watchedCount,
        )}"
    } else {
        titles
    }
}

private const val STACK_SHIFT_DP = 5
private const val BACK_POSTER_ALPHA = 0.7f

/** The first posters of a list, shifted over each other. */
@Composable
private fun PosterStack(posterUrls: List<String?>, name: String) {
    Box(Modifier.size(width = 60.dp, height = 90.dp), contentAlignment = Alignment.BottomStart) {
        posterUrls.withIndex().reversed().forEach { (offset, url) ->
            ArtworkImage(
                url,
                name,
                Modifier
                    .offset(x = (offset * STACK_SHIFT_DP).dp, y = (-offset * STACK_SHIFT_DP).dp)
                    .size(width = 50.dp, height = 75.dp)
                    .alpha(if (offset == 0) 1f else BACK_POSTER_ALPHA),
            )
        }
    }
}

@Preview
@Composable
private fun ListsContentPreview() {
    JellyCastTheme {
        ListsContent(
            state =
                ListsUiState(
                    load = ListsLoad.Loaded,
                    lists =
                        listOf(
                            ListSummary(
                                "a",
                                "Nolan",
                                listOf(null, null),
                                2,
                                1,
                                ListStatus.NotStarted,
                            ),
                            ListSummary("b", "Christmas", emptyList(), 0, 0, ListStatus.Empty),
                        ),
                ),
            onBack = {},
            onOpen = {},
            onCreate = {},
            onRefresh = {},
        )
    }
}

@Composable
private fun EmptyLists(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.lists_empty_title),
            style = JellyCastText.Title3.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
        )
        Text(
            stringResource(R.string.lists_empty_body),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
