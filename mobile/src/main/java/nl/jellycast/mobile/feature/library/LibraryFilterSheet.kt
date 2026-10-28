package nl.jellycast.mobile.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.SheetRow
import nl.jellycast.mobile.core.designsystem.component.SheetTitle
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors

/**
 * The iOS filter menu as a Material bottom sheet: Kind, Status, Sort and Genre as single-choice
 * chips, then "Lists".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    filters: LibraryFilters,
    genres: List<GenreCount>,
    onFiltersChange: (LibraryFilters) -> Unit,
    onOpenLists: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JellyCastColors.Surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            SheetTitle(stringResource(R.string.library_filter_and_sort))
            ChoiceGroup(
                stringResource(R.string.library_kind),
                LibraryKind.entries,
                filters.kind,
                title = { kindTitle(it) },
                onSelect = { onFiltersChange(filters.copy(kind = it)) },
            )
            ChoiceGroup(
                stringResource(R.string.library_status),
                listOf(null) + WatchStatus.entries,
                filters.status,
                title = { statusTitle(it) },
                onSelect = { onFiltersChange(filters.copy(status = it)) },
            )
            ChoiceGroup(
                stringResource(R.string.library_sort),
                LibrarySort.entries,
                filters.sort,
                title = { sortTitle(it) },
                onSelect = { onFiltersChange(filters.copy(sort = it)) },
            )
            val allGenres = stringResource(R.string.library_genre_all)
            ChoiceGroup(
                stringResource(R.string.library_genre),
                listOf(null) + genres.map { it.genre },
                filters.genre,
                title = { genre ->
                    if (genre == null) {
                        allGenres
                    } else {
                        "${genreName(genre)} · ${genres.first { it.genre == genre }.count}"
                    }
                },
                onSelect = { onFiltersChange(filters.copy(genre = it)) },
            )
            SheetRow(
                title = stringResource(R.string.library_lists),
                onClick = onOpenLists,
                modifier = Modifier.padding(top = 24.dp),
                icon = R.drawable.ic_library_list_numbered,
            )
        }
    }
}

@Composable
private fun <T> ChoiceGroup(
    label: String,
    options: List<T>,
    selected: T,
    title: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ListSectionLabel(label, Modifier.padding(top = 20.dp, bottom = 8.dp))
        ChoiceChips(options, selected, title, onSelect)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    title: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(title(option)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JellyCastColors.Accent,
                        selectedLabelColor = JellyCastColors.OnAccent,
                        labelColor = JellyCastColors.Text,
                    ),
            )
        }
    }
}
