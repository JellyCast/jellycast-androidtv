package nl.jellycast.mobile.ui.info

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.DetailRepository
import nl.jellycast.core.model.GenreNames
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.ui.chat.isChatAvailable
import org.koin.compose.koinInject

/** What the info panel describes. */
sealed interface InfoSubject {
    data class MovieInfo(val movieId: String) : InfoSubject

    data class EpisodeInfo(val episodeId: String, val seriesId: String) : InfoSubject

    /** The series page: the whole series, with its seasons in the Episodes tab. */
    data class SeriesInfo(val seriesId: String) : InfoSubject
}

/** One scrolling column, or (tablet landscape) header and summary left, tabs right. */
enum class InfoPanelArrangement { Column, Split }

/**
 * The summary (title, meta line, tech badges, overview, rating link) and the tabs (Episodes,
 * Cast, Related, Chat) shown on the movie detail, the series page and under the video in the
 * player.
 *
 * [header] scrolls above it: the [DetailHero] on detail pages. Without a header the panel is in
 * the player: it shows the title, and a related movie plays instead of opening its page.
 * [actions] go at the end of the link row. [playingItemId] marks the episode that plays and
 * [playingOn] names the TV it plays on. [arrangement] `null` splits on a tablet in landscape.
 */
@Composable
fun ItemInfoPanel(
    subject: InfoSubject,
    modifier: Modifier = Modifier,
    playingItemId: String? = null,
    playingOn: String? = null,
    arrangement: InfoPanelArrangement? = null,
    header: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val viewModel = rememberItemInfoViewModel(subject)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chatTab = if (isChatAvailable()) listOf(InfoTab.Chat) else emptyList()
    val tabs = state.tabs + chatTab
    var chosenTab by rememberSaveable(subject) { mutableStateOf<InfoTab?>(null) }
    val panel =
        PanelState(
            state = state,
            tabs = tabs,
            currentTab = chosenTab?.takeIf { it in tabs } ?: tabs.first(),
            onTab = { chosenTab = it },
            playingItemId = playingItemId,
            playingOn = playingOn,
            inPlayer = header == null,
            onRetryCast = viewModel::onRetryCast,
        )
    val layout = LocalLayout.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val split =
            arrangement == InfoPanelArrangement.Split ||
                (arrangement == null && layout.isRegular && maxWidth > maxHeight)
        if (split) {
            SplitPanel(panel, header, actions, maxWidth * SPLIT_LEADING_FRACTION)
        } else {
            ColumnPanel(panel, header, actions)
        }
    }
}

@Composable
private fun rememberItemInfoViewModel(subject: InfoSubject): ItemInfoViewModel {
    val store: LibraryStore = koinInject()
    val details: DetailRepository = koinInject()
    val preferences: AppPreferences = koinInject()
    val genres: GenreNames = koinInject()
    return viewModel(key = "info:$subject") {
        ItemInfoViewModel(subject, store, details, preferences, genres)
    }
}

internal data class PanelState(
    val state: ItemInfoUiState,
    val tabs: List<InfoTab>,
    val currentTab: InfoTab,
    val onTab: (InfoTab) -> Unit,
    val playingItemId: String?,
    val playingOn: String?,
    val inPlayer: Boolean,
    val onRetryCast: () -> Unit,
)

/** Remembers where the tab bar is, so choosing a tab can scroll it up under the header. */
private class TabBarAnchor {
    var viewport: LayoutCoordinates? = null
    var bar: LayoutCoordinates? = null

    fun contentY(scroll: ScrollState): Int {
        val viewport = viewport ?: return 0
        val bar = bar?.takeIf { it.isAttached } ?: return 0
        return viewport.localPositionOf(bar, Offset.Zero).y.roundToInt() + scroll.value
    }
}

@Composable
private fun ColumnPanel(
    panel: PanelState,
    header: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val layout = LocalLayout.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val anchor = remember { TabBarAnchor() }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val clearance = tabBarClearance(header != null)
    val clearancePx = with(density) { clearance.roundToPx() }
    val tabMinHeight = with(density) { viewportHeight.toDp() } - TAB_BAR_SPACE - clearance
    Column(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeight = it.height }
            .onGloballyPositioned { anchor.viewport = it }
            .verticalScroll(scroll),
    ) {
        header?.invoke()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.margin)
                .padding(top = 18.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            InfoSummary(
                panel.state,
                showsTitle = header == null,
                actions = actions,
                modifier = Modifier.widthIn(max = layout.textMaxWidth),
            )
            InfoTabBar(
                tabs = panel.tabs,
                selected = panel.currentTab,
                onSelect = { tab ->
                    panel.onTab(tab)
                    val target = (anchor.contentY(scroll) - clearancePx).coerceAtLeast(0)
                    scope.launch { scroll.animateScrollTo(target, tween(TAB_SCROLL_MS)) }
                },
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .widthIn(max = layout.textMaxWidth)
                        .onGloballyPositioned { anchor.bar = it },
            )
            InfoTabContent(
                panel,
                isLarge = layout.isRegular,
                margin = layout.margin,
                modifier = Modifier.heightIn(min = tabMinHeight.coerceAtLeast(0.dp)),
            )
        }
    }
}

@Composable
private fun SplitPanel(
    panel: PanelState,
    header: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    leadingWidth: Dp,
) {
    val layout = LocalLayout.current
    val clearance = tabBarClearance(header != null)
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(leadingWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
            header?.invoke()
            InfoSummary(
                panel.state,
                showsTitle = header == null,
                actions = actions,
                modifier =
                    Modifier
                        .padding(horizontal = layout.margin)
                        .padding(top = 18.dp, bottom = 40.dp),
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            InfoTabBar(
                tabs = panel.tabs,
                selected = panel.currentTab,
                onSelect = panel.onTab,
                modifier =
                    Modifier
                        .padding(horizontal = SPLIT_TRAILING_PADDING)
                        .padding(top = 18.dp + clearance, bottom = 8.dp),
            )
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = SPLIT_TRAILING_PADDING)
                    .padding(top = 10.dp, bottom = 40.dp),
            ) {
                InfoTabContent(panel, isLarge = false, margin = SPLIT_TRAILING_PADDING)
            }
        }
    }
}

/** On detail pages the column runs under the status bar and the floating Back button. */
@Composable
private fun tabBarClearance(hasHeader: Boolean): Dp = if (hasHeader) {
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + BACK_BUTTON_ROW
} else {
    0.dp
}

private const val SPLIT_LEADING_FRACTION = 0.645f
private const val TAB_SCROLL_MS = 250
private val SPLIT_TRAILING_PADDING = 24.dp
private val TAB_BAR_SPACE = 72.dp
private val BACK_BUTTON_ROW = 60.dp
