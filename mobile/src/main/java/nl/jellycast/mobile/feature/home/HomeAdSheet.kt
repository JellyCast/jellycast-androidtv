package nl.jellycast.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.util.Locale
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.AccentButton
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText

/** What the ad is, its link, and why this device sees it: its country, nothing else. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeAdSheet(ad: HomeTile.Ad, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val campaign = ad.campaign
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JellyCastColors.Surface,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = campaign.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(JellyCastColors.Background),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                campaign.title?.let {
                    Text(it, style = JellyCastText.Title3, color = JellyCastColors.Text)
                }
                Text(
                    stringResource(R.string.ad_meta, campaign.advertiser),
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.TextMuted,
                )
            }
            campaign.url?.let { url ->
                AccentButton(stringResource(R.string.ad_visit), onClick = {
                    uriHandler.openUri(url)
                })
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.ad_why_title),
                    style = JellyCastText.Headline,
                    color = JellyCastColors.Text,
                )
                Text(
                    adReason(ad.country),
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun adReason(country: String?): String {
    val name = country?.let { Locale("", it).displayCountry }?.takeIf { it.isNotBlank() }
    return if (name == null) {
        stringResource(R.string.ad_why_no_country)
    } else {
        stringResource(R.string.ad_why, name)
    }
}
