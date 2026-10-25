package nl.jellycast.tv.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.component.CornerBackdrop
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.LandscapeCard
import nl.jellycast.tv.core.designsystem.component.PosterCard
import nl.jellycast.tv.core.designsystem.component.ProvideBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.RowBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.TvRow
import nl.jellycast.tv.core.designsystem.component.rememberCachedLazyListState
import nl.jellycast.tv.core.designsystem.component.rememberColumnBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.model.Artwork
import nl.jellycast.tv.core.model.Episode
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.Series
import nl.jellycast.tv.core.model.ServerGenreNames
import nl.jellycast.tv.core.model.ShelfKind
import nl.jellycast.tv.core.model.progress
import nl.jellycast.tv.core.model.remainingMs
import nl.jellycast.tv.core.ui.episodeCode
import nl.jellycast.tv.core.ui.genreName
import nl.jellycast.tv.core.ui.isArrowKey
import nl.jellycast.tv.core.ui.remainingText
import nl.jellycast.tv.core.ui.runtimeText
import nl.jellycast.tv.core.ui.seasonCountText
import nl.jellycast.tv.core.ui.stopHorizontalExit
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SeriesHero(item: SeriesItem?, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .padding(start = Dimens.SafeHorizontal, top = 6.dp)
                .width(424.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val (logo, title) =
            when (item) {
                is SeriesItem.NextUp -> item.episode.artwork.logo to item.episode.seriesTitle
                is SeriesItem.Show -> item.series.artwork.logo to item.series.title
                null -> null to ""
            }
        Box(
            Modifier.size(width = 260.dp, height = 41.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (logo != null) {
                ArtworkImage(
                    url = logo,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    showPlaceholder = false,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.height(18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (item) {
                is SeriesItem.NextUp -> EpisodeMeta(item.episode)
                is SeriesItem.Show -> SeriesMeta(item.series)
                null -> Unit
            }
        }
        Text(
            text =
                when (item) {
                    is SeriesItem.NextUp -> item.episode.overview
                    is SeriesItem.Show -> item.series.overview
                    null -> null
                }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(63.dp),
        )
    }
}

@Composable
private fun RowScope.EpisodeMeta(episode: Episode) {
    Text(
        text = episodeCode(episode.seasonNumber, episode.episodeNumber),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = episode.title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
    val remaining = remainingMs(episode.resumePositionMs, episode.runtimeMs)
    val time =
        when {
            remaining != null -> remainingText(remaining)
            episode.runtimeMs != null -> runtimeText(episode.runtimeMs)
            else -> null
        }
    if (time != null) MetaText("· $time")
}

@Composable
private fun SeriesMeta(series: Series) {
    val parts =
        buildList {
            series.year?.let { add(it.toString()) }
            series.seasonCount?.takeIf { it > 0 }?.let { add(seasonCountText(it)) }
            series.genres.firstOrNull()?.let { add(genreName(it)) }
            series.ageRating?.let { add(it.label) }
        }
    MetaText(parts.joinToString("  ·  "))
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = MaterialTheme.typography.titleSmall.lineHeight,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
internal fun SeriesSkeleton(modifier: Modifier = Modifier) {
    val clock = rememberSkeletonClock()
    val color = MaterialTheme.colorScheme.surface
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(start = Dimens.SafeHorizontal, top = Dimens.SectionBarHeight + 6.dp),
    ) {
        Box(Modifier.size(260.dp, 41.dp).skeletonBlock(clock, 0, color))
        Box(Modifier.padding(top = 10.dp).size(220.dp, 18.dp).skeletonBlock(clock, 0, color, 4.dp))
        listOf(400, 380, 240).forEach { width ->
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(width.dp, 15.dp)
                    .skeletonBlock(clock, 0, color, 4.dp),
            )
        }
        Box(Modifier.padding(top = 34.dp).size(120.dp, 20.dp).skeletonBlock(clock, 0, color, 4.dp))
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            repeat(5) { index ->
                Box(
                    Modifier
                        .size(Dimens.LandscapeWidth, Dimens.LandscapeHeight)
                        .skeletonBlock(clock, index, color),
                )
            }
        }
    }
}
