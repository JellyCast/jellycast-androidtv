package nl.jellycast.mobile.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PersonMatch
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ArtworkImage
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.ui.episodeCode

@Composable
fun RecentSearchRow(
    term: String,
    onSelect: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(48.dp).pressable(onSelect),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_library_history),
                null,
                Modifier.width(24.dp).size(20.dp),
                tint = JellyCastColors.TextMuted,
            )
            Text(term, style = JellyCastText.Body, color = JellyCastColors.Text, maxLines = 1)
        }
        Box(
            Modifier.size(width = 28.dp, height = 48.dp).pressable(onForget),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_library_close),
                stringResource(R.string.library_remove),
                Modifier.size(14.dp),
                tint = JellyCastColors.TextMuted,
            )
        }
    }
}

private val PersonImageSize = 60.dp

@Composable
fun PersonRow(person: PersonMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().pressable(onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(PersonImageSize).clip(CircleShape).background(JellyCastColors.Rail),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(person.name),
                style = JellyCastText.Title3.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.TextMuted,
            )
            if (person.imageUrl != null) {
                AsyncImage(
                    model = person.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            person.name,
            style = JellyCastText.Headline,
            color = JellyCastColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_library_chevron_right),
            null,
            Modifier.width(28.dp).size(18.dp),
            tint = JellyCastColors.TextMuted,
        )
    }
}

@Composable
fun EpisodeResultRow(
    episode: Episode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(onClick, onLongClick, enabled = enabled)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtworkImage(
            episode.stillUrl ?: episode.artwork.landscape,
            episode.title,
            Modifier.size(width = 96.dp, height = 54.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                episode.title,
                style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(episode.seriesTitle, episodeCode(episode))
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = JellyCastText.Footnote,
                color = JellyCastColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }
