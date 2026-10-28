package nl.jellycast.mobile.feature.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.GlassBorder
import nl.jellycast.mobile.core.designsystem.component.GlassFill
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout

private val BarHeight = 48.dp

/**
 * The search field and the filter button, floating above the tab bar within thumb reach. It
 * rides on top of the keyboard while that is open.
 */
@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    isNarrowed: Boolean,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    // The root view: LocalWindowInfo.containerSize was too small on Android 12 in landscape.
    val windowHeight = LocalView.current.rootView.height
    var gapBelowPx by remember { mutableFloatStateOf(0f) }
    Row(
        modifier
            .onGloballyPositioned { gapBelowPx = windowHeight - it.boundsInWindow().bottom }
            .offset {
                // The bar already sits above the tab bar, which the keyboard also covers.
                val lift = (ime.getBottom(density) - gapBelowPx).coerceAtLeast(0f)
                IntOffset(0, -lift.roundToInt())
            }.fillMaxWidth()
            .padding(horizontal = LocalLayout.current.margin)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(BarHeight)
                .glass(CircleShape)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_search),
                null,
                Modifier.size(18.dp),
                tint = JellyCastColors.TextMuted,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = JellyCastText.Body.copy(color = JellyCastColors.Text),
                cursorBrush = SolidColor(JellyCastColors.Accent),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            onSubmit()
                            keyboard?.hide()
                        },
                    ),
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) },
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.library_search_placeholder),
                                style = JellyCastText.Body,
                                color = JellyCastColors.TextMuted,
                                maxLines = 1,
                            )
                        }
                        field()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    painterResource(R.drawable.ic_library_cancel),
                    stringResource(R.string.library_clear),
                    Modifier.size(20.dp).pressable({ onQueryChange("") }),
                    tint = JellyCastColors.TextMuted,
                )
            }
        }
        Box(
            Modifier.size(BarHeight).glass(CircleShape).pressable(onOpenFilters),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_library_filter),
                stringResource(R.string.library_filter_and_sort),
                Modifier.size(22.dp),
                tint = if (isNarrowed) JellyCastColors.Accent else JellyCastColors.Text,
            )
        }
    }
}

// Over the grid the glass needs a solid base, or posters show through the field's text.
private fun Modifier.glass(shape: Shape): Modifier = this
    .background(JellyCastColors.Surface.copy(alpha = 0.92f), shape)
    .background(GlassFill, shape)
    .border(1.dp, GlassBorder, shape)

private val BannerShape = RoundedCornerShape(16.dp)

/** Over a library shown from the cache: why it could not refresh and since when. Tap retries. */
@Composable
fun SyncProblemBanner(
    problem: SyncProblem,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val updated =
        DateUtils
            .getRelativeTimeSpanString(
                problem.syncedAtMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
    Row(
        modifier
            .fillMaxWidth()
            .glass(BannerShape)
            .pressable(onRetry, enabled = !isRetrying)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                problem.message ?: stringResource(R.string.library_sync_failed),
                style = JellyCastText.Footnote.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
            )
            Text(
                stringResource(R.string.library_sync_updated, updated),
                style = JellyCastText.Caption,
                color = JellyCastColors.TextMuted,
            )
        }
        if (isRetrying) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                color = JellyCastColors.Text,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_library_replay),
                stringResource(R.string.retry),
                Modifier.size(18.dp),
                tint = JellyCastColors.Text,
            )
        }
    }
}
