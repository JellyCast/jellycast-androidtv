package nl.jellycast.mobile.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PersonMatch
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ArtworkImage
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.POSTER_RATIO
import nl.jellycast.mobile.core.designsystem.component.WatchProgressBar
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout

/** Everything the grid can do; the screen wires it to the ViewModel and navigation. */
class LibraryGridActions(
    val onOpenEntry: (LibraryEntry) -> Unit,
    val onHoldEntry: (LibraryEntry) -> Unit,
    val onOpenPerson: (PersonMatch) -> Unit,
    val onOpenEpisode: (Episode) -> Unit,
    val onHoldEpisode: (Episode) -> Unit,
    val onFiltersChange: (LibraryFilters) -> Unit,
    val onSelectRecent: (String) -> Unit,
    val onForgetRecent: (String) -> Unit,
    val isDimmed: (String) -> Boolean,
)

@Composable
fun LibraryGrid(
    state: LibraryUiState,
    content: LibraryBody.Ready,
    showsRecent: Boolean,
    contentPadding: PaddingValues,
    actions: LibraryGridActions,
    onScroll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = LocalLayout.current.margin
    val metrics = tileMetrics(LocalLayout.current.isRegular)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val dismissOnScroll =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput) currentOnScroll()
                    return Offset.Zero
                }
            }
        }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth - margin * 2
        val columns =
            maxOf(
                MIN_COLUMNS,
                ((width + metrics.spacing) / (metrics.minWidth + metrics.spacing)).toInt(),
            )
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(dismissOnScroll),
            contentPadding = contentPadding,
        ) {
            item(key = "chips") {
                ActiveFilterChips(
                    state.filters,
                    content.statusCount,
                    actions.onFiltersChange,
                    Modifier.padding(horizontal = margin),
                )
            }
            if (showsRecent && state.term.isEmpty() && state.recentSearches.isNotEmpty()) {
                recentSearches(state.recentSearches, margin, actions)
            }
            if (content.isEmpty) {
                item(key = "empty") { EmptyResult(state.term) }
            }
            content.sections.forEach { section ->
                tileSection(section, columns, metrics, margin, actions)
            }
            if (content.people.isNotEmpty()) {
                item(key = "people-label") {
                    ResultLabel(stringResource(R.string.library_people), margin)
                }
                items(content.people, key = { "person-${it.id}" }) { person ->
                    PersonRow(
                        person,
                        onClick = { actions.onOpenPerson(person) },
                        modifier = Modifier.padding(horizontal = margin),
                    )
                }
            }
            if (content.episodes.isNotEmpty()) {
                item(key = "episodes-label") {
                    ResultLabel(stringResource(R.string.library_episodes), margin)
                }
                items(content.episodes, key = { "episode-${it.id}" }) { episode ->
                    EpisodeResultRow(
                        episode,
                        onClick = { actions.onOpenEpisode(episode) },
                        onLongClick = { actions.onHoldEpisode(episode) },
                        enabled = !actions.isDimmed(episode.seriesId),
                        modifier = Modifier.padding(horizontal = margin),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.recentSearches(
    terms: List<String>,
    margin: Dp,
    actions: LibraryGridActions,
) {
    item(key = "recent-label") {
        ListSectionLabel(
            stringResource(R.string.library_recent_searches),
            Modifier.padding(start = margin, end = margin, top = 10.dp, bottom = 4.dp),
        )
    }
    items(terms, key = { "recent-$it" }) { term ->
        RecentSearchRow(
            term,
            onSelect = { actions.onSelectRecent(term) },
            onForget = { actions.onForgetRecent(term) },
            modifier = Modifier.padding(horizontal = margin),
        )
    }
}

private fun LazyListScope.tileSection(
    section: LibrarySection,
    columns: Int,
    metrics: TileMetrics,
    margin: Dp,
    actions: LibraryGridActions,
) {
    item(key = "section-${section.key}", contentType = "label") {
        ListSectionLabel(
            sectionTitle(section.key),
            Modifier.padding(start = margin, end = margin, top = 18.dp, bottom = 10.dp),
        )
    }
    val rows = section.entries.chunked(columns)
    items(
        rows,
        key = { row -> "row-${section.key}-${row.first().id}" },
        contentType = { "tiles" },
    ) { row ->
        Row(
            Modifier
                .padding(horizontal = margin)
                .padding(bottom = if (row === rows.last()) 0.dp else metrics.rowSpacing),
            horizontalArrangement = Arrangement.spacedBy(metrics.spacing),
        ) {
            row.forEach { entry ->
                LibraryTile(
                    entry,
                    onClick = { actions.onOpenEntry(entry) },
                    onLongClick = { actions.onHoldEntry(entry) },
                    dimmed = actions.isDimmed(entry.id),
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ResultLabel(text: String, margin: Dp) {
    ListSectionLabel(
        text,
        Modifier.padding(start = margin, end = margin, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyResult(term: String) {
    Text(
        if (term.isEmpty()) {
            stringResource(R.string.library_nothing_here)
        } else {
            stringResource(R.string.library_no_results, term)
        },
        style = JellyCastText.Body,
        color = JellyCastColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 20.dp, end = 20.dp),
    )
}

private data class TileMetrics(val minWidth: Dp, val spacing: Dp, val rowSpacing: Dp)

private fun tileMetrics(isRegular: Boolean) = if (isRegular) {
    TileMetrics(minWidth = 150.dp, spacing = 20.dp, rowSpacing = 24.dp)
} else {
    TileMetrics(minWidth = 100.dp, spacing = 12.dp, rowSpacing = 18.dp)
}

private const val MIN_COLUMNS = 3
private const val DIMMED_ALPHA = 0.4f

/** A poster with the title and year under it; the status shows on the poster. */
@Composable
fun LibraryTile(
    entry: LibraryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    Column(
        modifier
            .alpha(if (dimmed) DIMMED_ALPHA else 1f)
            .pressable(onClick, onLongClick, enabled = !dimmed),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO).padding(bottom = 2.dp)) {
            ArtworkImage(entry.posterUrl, entry.title, Modifier.fillMaxSize())
            if (entry.status == WatchStatus.Watched) {
                WatchedMark(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            entry.progress?.let {
                WatchProgressBar(
                    it,
                    Modifier.align(Alignment.BottomCenter).padding(6.dp),
                    onImage = true,
                )
            }
        }
        Text(
            entry.title,
            style = JellyCastText.Footnote.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.year?.let {
            Text(it.toString(), style = JellyCastText.Caption, color = JellyCastColors.TextMuted)
        }
    }
}

@Composable
private fun WatchedMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(JellyCastColors.Background.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_check),
            stringResource(R.string.library_status_watched),
            Modifier.size(12.dp),
            tint = JellyCastColors.Text,
        )
    }
}

/** What narrows the grid now, each with a cross to drop it; nothing when everything shows. */
@Composable
private fun ActiveFilterChips(
    filters: LibraryFilters,
    statusCount: Int,
    onFiltersChange: (LibraryFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!filters.isNarrowed) return
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filters.kind != LibraryKind.All) {
            FilterChipPill(kindTitle(filters.kind)) {
                onFiltersChange(filters.copy(kind = LibraryKind.All))
            }
        }
        filters.status?.let { status ->
            FilterChipPill(statusTitle(status), count = statusCount) {
                onFiltersChange(filters.copy(status = null))
            }
        }
        filters.genre?.let { genre ->
            FilterChipPill(genreName(genre)) { onFiltersChange(filters.copy(genre = null)) }
        }
    }
}

@Composable
private fun FilterChipPill(title: String, count: Int? = null, onClear: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(JellyCastColors.Rail)
            .pressable(onClear)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold)
        Text(title, style = style, color = JellyCastColors.Text, maxLines = 1)
        if (count != null) {
            Text(
                count.toString(),
                style = style,
                color = JellyCastColors.Text.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
        Icon(
            painterResource(R.drawable.ic_library_close),
            stringResource(R.string.library_clear),
            Modifier.size(11.dp),
            tint = JellyCastColors.Text,
        )
    }
}
