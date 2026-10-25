package nl.jellycast.tv.feature.library

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ChoiceChip
import nl.jellycast.tv.core.designsystem.component.PillButton
import nl.jellycast.tv.core.designsystem.component.TvTextField
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.model.LibraryKind
import nl.jellycast.tv.core.model.LibrarySort
import nl.jellycast.tv.core.ui.isArrowKey
import nl.jellycast.tv.core.ui.stopHorizontalExit

private val SearchWidth = 240.dp

@Composable
internal fun LibraryControls(
    uiState: LibraryUiState,
    kindRequesters: Map<LibraryKind, FocusRequester>,
    searchRequester: FocusRequester,
    sortRequester: FocusRequester,
    onKindSelect: (LibraryKind) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SafeHorizontal)
                .padding(top = 14.dp)
                .focusProperties {
                    // From the section bar: land on the chosen kind, not on whatever is below
                    // the tab.
                    onEnter = {
                        if (isArrowKey && requestedFocusDirection == FocusDirection.Down) {
                            kindRequesters.getValue(uiState.kind).requestFocus()
                        }
                    }
                }.stopHorizontalExit(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryKind.entries.forEach { kind ->
            ChoiceChip(
                label = stringResource(kind.labelRes()),
                isSelected = kind == uiState.kind,
                onClick = { onKindSelect(kind) },
                modifier = Modifier.focusRequester(kindRequesters.getValue(kind)),
            )
        }
        Spacer(Modifier.width(16.dp))
        TvTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            label = null,
            compact = true,
            placeholder = stringResource(R.string.library_search),
            leadingIcon = painterResource(R.drawable.ic_search),
            // A focus group, so a focus request reaches the text field inside.
            modifier = Modifier.width(SearchWidth).focusRequester(searchRequester).focusGroup(),
        )
        PillButton(
            text = stringResource(R.string.library_sort, stringResource(uiState.sort.labelRes())),
            onClick = onSortClick,
            icon = painterResource(R.drawable.ic_sort),
            modifier = Modifier.height(34.dp).focusRequester(sortRequester),
        )
        Spacer(Modifier.weight(1f))
        (uiState.content as? LibraryContent.Loaded)?.let { loaded ->
            ResultCount(uiState = uiState, total = loaded.total)
        }
    }
}

@Composable
private fun ResultCount(uiState: LibraryUiState, total: Int) {
    Text(
        text =
            if (uiState.query.isBlank()) {
                pluralStringResource(uiState.kind.countRes(), total, total)
            } else {
                stringResource(R.string.library_found, total, uiState.query.trim())
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

private fun LibraryKind.labelRes(): Int = when (this) {
    LibraryKind.Movies -> R.string.library_kind_movies
    LibraryKind.Series -> R.string.library_kind_series
}

private fun LibraryKind.countRes(): Int = when (this) {
    LibraryKind.Movies -> R.plurals.library_count_movies
    LibraryKind.Series -> R.plurals.library_count_series
}

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Title -> R.string.library_sort_title
    LibrarySort.Added -> R.string.library_sort_added
    LibrarySort.Year -> R.string.library_sort_year
    LibrarySort.Rating -> R.string.library_sort_rating
}
