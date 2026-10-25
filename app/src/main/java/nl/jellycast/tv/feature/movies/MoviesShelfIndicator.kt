package nl.jellycast.tv.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.model.ShelfKind
import org.koin.compose.viewmodel.koinViewModel

// Shows the current shelf with one before and two after it: with genres there are too many
// shelves to show them all.
@Composable
fun MoviesShelfIndicator(
    modifier: Modifier = Modifier,
    viewModel: MoviesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState as? MoviesUiState.Success ?: return
    val first = (state.shelfIndex - 1).coerceAtLeast(0)
    val last = (state.shelfIndex + 2).coerceAtMost(state.shelves.lastIndex)
    Row(
        modifier = modifier.padding(start = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 14.dp)
                .size(width = 1.dp, height = 20.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Icon(
            painter = painterResource(R.drawable.ic_shelves),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        if (first > 0) Text(text = "…", style = MaterialTheme.typography.labelLarge, color = muted)
        for (index in first..last) {
            val isCurrent = index == state.shelfIndex
            Text(
                text = shelfTitle(state.shelves[index].kind),
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    ),
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else muted,
            )
        }
        if (last < state.shelves.lastIndex) {
            Text(text = "…", style = MaterialTheme.typography.labelLarge, color = muted)
        }
    }
}

@Composable
internal fun shelfTitle(kind: ShelfKind): String = when (kind) {
    ShelfKind.ContinueWatching -> stringResource(R.string.shelf_continue)

    ShelfKind.New -> stringResource(R.string.movies_shelf_new)

    // Only the series section has this shelf.
    ShelfKind.NotStarted -> stringResource(R.string.series_shelf_not_started)

    is ShelfKind.Genre -> kind.name
}
