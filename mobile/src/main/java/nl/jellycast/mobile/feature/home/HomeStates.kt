package nl.jellycast.mobile.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.GlassBorder
import nl.jellycast.mobile.core.designsystem.component.GlassFill
import nl.jellycast.mobile.core.designsystem.component.LANDSCAPE_RATIO
import nl.jellycast.mobile.core.designsystem.component.SurfaceBlock
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout

/** The shape of Home's grid in flat blocks while the first library loads. */
@Composable
internal fun HomeSkeleton(modifier: Modifier = Modifier) {
    val isRegular = LocalLayout.current.isRegular
    BoxWithConstraints(modifier.fillMaxSize().statusBarsPadding()) {
        val spec = homeGridSpec(isRegular, maxWidth, maxHeight)
        val tileWidth = spec.tileWidth(maxWidth)
        Column(
            Modifier.padding(horizontal = spec.margin).padding(top = spec.topGap),
            verticalArrangement = Arrangement.spacedBy(spec.rowGap),
        ) {
            SurfaceBlock(
                if (spec.hasSideStack) {
                    Modifier.width(tileWidth * 2 + spec.columnGap).aspectRatio(LANDSCAPE_RATIO)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(LANDSCAPE_RATIO)
                },
                radius = 12.dp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spec.columnGap)) {
                repeat(spec.columns) {
                    SurfaceBlock(
                        Modifier.width(tileWidth).aspectRatio(LANDSCAPE_RATIO),
                        radius = 10.dp,
                    )
                }
            }
        }
    }
}

private val BannerShape = RoundedCornerShape(16.dp)

/** Over a library shown from the cache: why it couldn't refresh and how old it is. Tap retries. */
@Composable
internal fun SyncProblemBanner(
    problem: SyncProblem,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(BannerShape)
            .background(JellyCastColors.Background.copy(alpha = 0.6f))
            .background(GlassFill)
            .border(1.dp, GlassBorder, BannerShape)
            .pressable(onRetry, enabled = !problem.isRetrying)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_home_wifi_off),
            contentDescription = null,
            tint = JellyCastColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                syncMessage(problem.message, problem.isOffline),
                style = JellyCastText.Footnote.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
            )
            Text(
                stringResource(R.string.home_updated, relativeTime(problem.syncedAtMs)),
                style = JellyCastText.Caption,
                color = JellyCastColors.TextMuted,
            )
        }
        if (problem.isRetrying) {
            CircularProgressIndicator(
                color = JellyCastColors.Text,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_home_refresh),
                contentDescription = null,
                tint = JellyCastColors.Text,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The store's message, or a fallback that says whether the phone or the server is the cause. */
@Composable
internal fun syncMessage(message: String?, isOffline: Boolean): String = message
    ?: stringResource(if (isOffline) R.string.home_offline else R.string.home_server_unreachable)

private fun relativeTime(atMs: Long): String = DateUtils.getRelativeTimeSpanString(
    atMs,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()
