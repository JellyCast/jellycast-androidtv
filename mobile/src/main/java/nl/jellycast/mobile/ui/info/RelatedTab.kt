package nl.jellycast.mobile.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.Movie
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.PosterImage
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText

/** The rest of the collection and the related movies, as poster grids. */
@Composable
internal fun RelatedTab(
    collectionTitle: String?,
    collection: List<Movie>,
    related: List<Movie>?,
    onSelect: (Movie) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (collection.isNotEmpty()) {
            PosterSection(
                collectionTitle ?: stringResource(R.string.info_same_collection),
                collection,
                onSelect,
            )
        }
        when {
            related == null -> TabSpinner()
            related.isNotEmpty() ->
                PosterSection(stringResource(R.string.info_tab_related), related, onSelect)
            collection.isEmpty() ->
                Text(
                    stringResource(R.string.info_nothing_related),
                    color = JellyCastColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
        }
    }
}

@Composable
private fun PosterSection(title: String, movies: List<Movie>, onSelect: (Movie) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ListSectionLabel(title)
        // Not lazy: the grid sits inside the panel's scrolling column and holds at most ~20.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns =
                ((maxWidth + COLUMN_SPACING) / (MIN_TILE_WIDTH + COLUMN_SPACING)).toInt()
                    .coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                movies.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_SPACING)) {
                        row.forEach { movie ->
                            RelatedTile(movie, { onSelect(movie) }, Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedTile(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.pressable(onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PosterImage(movie.artwork.poster, movie.title, Modifier.fillMaxWidth())
        Text(
            movie.title,
            style = JellyCastText.Footnote.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        movie.year?.let {
            Text(it.toString(), style = JellyCastText.Caption, color = JellyCastColors.TextMuted)
        }
    }
}

private val MIN_TILE_WIDTH = 110.dp
private val COLUMN_SPACING = 12.dp
