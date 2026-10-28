package nl.jellycast.mobile.ui.info

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.LocalDate
import nl.jellycast.core.model.Credit
import nl.jellycast.core.model.PersonDetails
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.GlassButton
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.settings.RatingSource

/** Actors with what the server knows about them, then the makers under a divider. */
@Composable
internal fun CastTab(
    cast: CastState,
    source: RatingSource,
    isEpisode: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (cast) {
        CastState.Loading -> TabSpinner(modifier)
        CastState.Failed ->
            Column(
                modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.info_cast_failed), color = JellyCastColors.TextMuted)
                GlassButton(stringResource(R.string.retry), onRetry)
            }
        is CastState.Loaded -> CastContent(cast.credits, source, isEpisode, modifier)
    }
}

@Composable
internal fun TabSpinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), color = JellyCastColors.TextMuted)
    }
}

@Composable
private fun CastContent(
    cast: CastCredits,
    source: RatingSource,
    isEpisode: Boolean,
    modifier: Modifier = Modifier,
) {
    var showsAll by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val open: (Credit) -> Unit = { credit ->
        val ids = cast.details[credit.personId]?.externalIds
        uriHandler.openUri(source.personUrl(ids?.imdb, ids?.tmdb, credit.name))
    }
    val actors = if (showsAll) cast.actors else cast.actors.take(COLLAPSED_ACTORS)
    val hidden = cast.actors.size - actors.size + if (showsAll) 0 else cast.guestStars.size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        actors.forEach { ActorRow(it, cast.details[it.personId], source, open) }
        if (showsAll && cast.guestStars.isNotEmpty()) {
            ListSectionLabel(stringResource(R.string.info_guest_stars))
            cast.guestStars.forEach { ActorRow(it, cast.details[it.personId], source, open) }
        }
        if (hidden > 0) ExpandLink(cast, onClick = { showsAll = true })
        if (cast.hasMakers) Makers(cast, source, isEpisode, open)
    }
}

@Composable
private fun ExpandLink(cast: CastCredits, onClick: () -> Unit) {
    val title =
        if (cast.guestStars.isEmpty()) {
            stringResource(R.string.info_all_actors, cast.actors.size)
        } else {
            stringResource(R.string.info_all_actors_and_guests, cast.actors.size, cast.guestStars.size)
        }
    Row(
        Modifier.pressable(onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Accent,
        )
        Icon(
            painterResource(R.drawable.ic_info_expand_more),
            null,
            Modifier.size(16.dp),
            tint = JellyCastColors.Accent,
        )
    }
}

@Composable
private fun Makers(cast: CastCredits, source: RatingSource, isEpisode: Boolean, open: (Credit) -> Unit) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(JellyCastColors.Rail))
        Text(
            stringResource(R.string.info_makers),
            style = JellyCastText.Title3.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
        )
        if (cast.directors.isNotEmpty()) {
            val label = if (isEpisode) R.string.info_directed_by_episode else R.string.info_directed_by
            MakerGroup(stringResource(label), cast.directors, cast, source, open)
        }
        if (cast.writers.isNotEmpty()) {
            val label = if (isEpisode) R.string.info_written_by_episode else R.string.info_written_by
            MakerGroup(stringResource(label), cast.writers, cast, source, open)
        }
        if (cast.producers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val label = if (isEpisode) R.string.info_produced_by_series else R.string.info_produced_by
                ListSectionLabel(stringResource(label))
                Text(
                    cast.producers.joinToString(" · ") { it.name },
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.Text,
                )
            }
        }
    }
}

@Composable
private fun MakerGroup(
    title: String,
    credits: List<Credit>,
    cast: CastCredits,
    source: RatingSource,
    open: (Credit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ListSectionLabel(title)
        credits.forEach { credit ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PersonPhoto(credit, 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        credit.name,
                        style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = JellyCastColors.Text,
                    )
                    personFacts(cast.details[credit.personId])?.let {
                        Text(it, style = JellyCastText.Footnote, color = JellyCastColors.TextMuted)
                    }
                }
                InlineExternalLink(source, { open(credit) })
            }
        }
    }
}

@Composable
private fun ActorRow(
    credit: Credit,
    details: PersonDetails?,
    source: RatingSource,
    open: (Credit) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PersonPhoto(credit, 64.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    credit.name,
                    style = JellyCastText.Headline,
                    color = JellyCastColors.Text,
                    modifier = Modifier.weight(1f),
                )
                InlineExternalLink(source, { open(credit) })
            }
            credit.role?.let {
                Text(
                    stringResource(R.string.info_as_role, it),
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.Accent,
                )
            }
            personFacts(details)?.let {
                Text(it, style = JellyCastText.Footnote, color = JellyCastColors.TextMuted)
            }
            details?.biography?.let {
                Text(
                    it,
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.TextMuted,
                    maxLines = BIOGRAPHY_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** "48 years · Uvalde, United States", from what is known. */
@Composable
private fun personFacts(details: PersonDetails?): String? {
    details ?: return null
    val age = ageInYears(details.birthDate, LocalDate.now())
    val line =
        joined(
            listOf(
                age?.let { pluralStringResource(R.plurals.info_age_years, it, it) },
                birthplaceText(details.birthplace, currentLocale()),
            ),
        )
    return line.ifEmpty { null }
}

@Composable
private fun PersonPhoto(credit: Credit, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(JellyCastColors.Rail),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(credit.name),
            style =
                if (size > 50.dp) {
                    JellyCastText.Headline
                } else {
                    JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold)
                },
            color = JellyCastColors.TextMuted,
        )
        if (credit.imageUrl != null) {
            AsyncImage(
                model = credit.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun initials(name: String): String =
    name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }

private const val COLLAPSED_ACTORS = 6
private const val BIOGRAPHY_LINES = 3
