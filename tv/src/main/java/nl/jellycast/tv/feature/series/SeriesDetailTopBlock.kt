package nl.jellycast.tv.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.progress
import nl.jellycast.core.model.remainingMs
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.component.CornerBackdrop
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.FocusCard
import nl.jellycast.tv.core.designsystem.component.PillButton
import nl.jellycast.tv.core.designsystem.component.ProvideBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.TvRow
import nl.jellycast.tv.core.designsystem.component.progressBar
import nl.jellycast.tv.core.designsystem.component.rememberCachedLazyListState
import nl.jellycast.tv.core.designsystem.component.rememberColumnBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.ui.episodeCode
import nl.jellycast.tv.core.ui.genreName
import nl.jellycast.tv.core.ui.remainingText
import nl.jellycast.tv.core.ui.runtimeText
import nl.jellycast.tv.core.ui.seasonCountText
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun TopBlock(series: Series?, episode: Episode?, modifier: Modifier = Modifier) {
    Column(modifier.width(464.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(width = 260.dp, height = 41.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val logo = series?.artwork?.logo
            if (logo != null) {
                ArtworkImage(
                    url = logo,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    showPlaceholder = false,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (series != null) {
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 6.dp).height(18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                episode != null -> EpisodeMeta(episode)
                series != null -> SeriesMeta(series)
            }
        }
        Text(
            text = (episode?.overview ?: series?.overview).orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(440.dp).height(63.dp),
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
            remaining != null && !episode.isPlayed -> remainingText(remaining)
            else -> episode.runtimeMs?.let { runtimeText(it) }
        }
    if (time != null) MutedText("· $time")
}

@Composable
private fun RowScope.SeriesMeta(series: Series) {
    series.year?.let { Text(text = it.toString(), style = MaterialTheme.typography.bodyMedium) }
    val parts =
        buildList {
            series.seasonCount?.takeIf { it > 0 }?.let { add(seasonCountText(it)) }
            series.genres.take(2).takeIf { it.isNotEmpty() }?.let { genres ->
                add(genres.map { genreName(it) }.joinToString(", "))
            }
            series.ageRating?.let { add(it.label) }
        }
    MutedText((if (series.year != null) "·  " else "") + parts.joinToString("  ·  "))
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
internal fun ContinueButton(
    next: Episode?,
    isUnstarted: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val code = next?.let { episodeCode(it.seasonNumber, it.episodeNumber) }
    val label =
        stringResource(if (isUnstarted) R.string.series_play else R.string.series_continue)
    val remaining = next?.let { remainingMs(it.resumePositionMs, it.runtimeMs) }
    PillButton(
        text = if (code.isNullOrEmpty()) label else "$label · $code",
        trailing = remaining?.let { remainingText(it) },
        onClick = onClick,
        icon = painterResource(R.drawable.ic_play),
        isPrimary = isFocused,
        enabled = next != null,
        modifier = modifier.width(ContinueWidth),
    )
}

// Fixed, so the buttons next to it never move; wide enough for the French label.
private val ContinueWidth = 300.dp
