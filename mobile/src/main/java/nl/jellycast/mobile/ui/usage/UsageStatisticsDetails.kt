package nl.jellycast.mobile.ui.usage

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.BuildConfig
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme

/** What the usage ping sends and what it doesn't; shared by the first question and Settings. */
@Composable
fun UsageStatisticsDetails(modifier: Modifier = Modifier) {
    val thisDevice = "${Build.MODEL}, Android ${Build.VERSION.RELEASE}, " +
        "JellyCast ${BuildConfig.VERSION_NAME}"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Point(stringResource(R.string.usage_detail_ping))
        Point(stringResource(R.string.usage_detail_device, thisDevice))
        Point(stringResource(R.string.usage_detail_country))
        Point(stringResource(R.string.usage_detail_retention))
        Point(stringResource(R.string.usage_detail_settings))
    }
}

@Composable
private fun Point(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(5.dp)
                .background(JellyCastColors.Accent, CircleShape),
        )
        Text(text, style = JellyCastText.Subheadline, color = JellyCastColors.TextMuted)
    }
}

@Preview(backgroundColor = 0xFF0E0E0F, showBackground = true)
@Composable
private fun UsageStatisticsDetailsPreview() {
    JellyCastTheme { UsageStatisticsDetails(Modifier.padding(24.dp)) }
}
