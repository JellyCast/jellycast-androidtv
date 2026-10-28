package nl.jellycast.mobile.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.AccentButton
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.downloads.DownloadKind
import nl.jellycast.mobile.core.downloads.DownloadStore
import nl.jellycast.mobile.core.downloads.DownloadedMedia
import nl.jellycast.mobile.core.downloads.PlanResult
import nl.jellycast.mobile.core.downloads.runtimeMs
import org.koin.compose.koinInject

/**
 * Compact or Original for one item, with the size of each and the free space, as a Material
 * bottom sheet. [onDownload] runs once the download is queued.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    item: PlayableItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: () -> Unit = {},
) {
    val store: DownloadStore = koinInject()
    val text = rememberDownloadText()
    var result by remember(item.id) { mutableStateOf<PlanResult?>(null) }
    var chosen by remember(item.id) { mutableStateOf(DownloadKind.Compact) }
    val free = remember(item.id) { store.freeBytes() }
    LaunchedEffect(item.id) {
        val loaded = store.plan(item)
        chosen = loaded.plan.defaultKind
        result = loaded
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JellyCastColors.Surface,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        R.string.downloads_sheet_title,
                        text.title(DownloadedMedia.of(item)),
                    ),
                    style = JellyCastText.Title3.copy(fontWeight = FontWeight.Bold),
                    color = JellyCastColors.Text,
                )
                if (free != null) {
                    Text(
                        text.freeOnThisDevice(free),
                        style = JellyCastText.Subheadline,
                        color = JellyCastColors.TextMuted,
                    )
                }
            }
            val loaded = result
            if (loaded == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), Alignment.Center) {
                    CircularProgressIndicator(color = JellyCastColors.Accent)
                }
            } else {
                val shortage = { kind: DownloadKind -> shortage(loaded.plan.bytes(kind), free) }
                if (loaded.plan.offersCompact) {
                    KindOption(
                        title = stringResource(R.string.downloads_compact),
                        size = loaded.plan.compactBytes?.let(text::estimate),
                        description = compactDescription(item),
                        shortage = shortage(DownloadKind.Compact)?.let(text::doesNotFitShort),
                        isChosen = chosen == DownloadKind.Compact,
                        onChoose = { chosen = DownloadKind.Compact },
                    )
                }
                KindOption(
                    title = stringResource(R.string.downloads_kind_original),
                    size = loaded.plan.originalBytes?.let(text::bytes),
                    description = originalDescription(loaded),
                    shortage = shortage(DownloadKind.Original)?.let(text::doesNotFitShort),
                    isChosen = chosen == DownloadKind.Original,
                    onChoose = { chosen = DownloadKind.Original },
                )
                val bytes = loaded.plan.bytes(chosen)
                val fits = shortage(chosen) == null
                AccentButton(
                    title = stringResource(R.string.downloads_download),
                    detail =
                        bytes?.let {
                            if (chosen ==
                                DownloadKind.Compact
                            ) {
                                text.estimate(it)
                            } else {
                                text.bytes(it)
                            }
                        },
                    icon = R.drawable.ic_downloads_to_device,
                    onClick = {
                        if (fits) {
                            store.enqueue(item, chosen, bytes)
                            onDownload()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.alpha(if (fits) 1f else DISABLED_ALPHA),
                )
            }
        }
    }
}

@Composable
private fun KindOption(
    title: String,
    size: String?,
    description: String,
    shortage: String?,
    isChosen: Boolean,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = shortage == null
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else SHORT_ALPHA)
            .clip(OptionShape)
            .background(
                if (isChosen) {
                    JellyCastColors.Accent.copy(alpha = 0.12f)
                } else {
                    JellyCastColors.Text.copy(alpha = 0.05f)
                },
            )
            .border(
                1.5.dp,
                if (isChosen) JellyCastColors.Accent else JellyCastColors.Text.copy(alpha = 0.1f),
                OptionShape,
            )
            .pressable(onClick = onChoose, enabled = enabled)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = isChosen,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = JellyCastColors.Accent,
                    unselectedColor = JellyCastColors.TextMuted,
                ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(
                    title,
                    style = JellyCastText.Headline,
                    color = JellyCastColors.Text,
                    modifier = Modifier.weight(1f),
                )
                if (size != null) {
                    Text(size, style = JellyCastText.Headline, color = JellyCastColors.Text)
                }
            }
            Text(
                shortage ?: description,
                style = JellyCastText.Subheadline,
                color = if (shortage ==
                    null
                ) {
                    JellyCastColors.TextMuted
                } else {
                    JellyCastColors.Warning
                },
            )
        }
    }
}

@Composable
private fun compactDescription(item: PlayableItem): String {
    val seconds = (item.runtimeMs ?: 0) / MS_PER_SECOND
    val minutes = ceil(seconds / CONVERSION_SPEED / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)
    return stringResource(R.string.downloads_compact_description, minutes)
}

@Composable
private fun originalDescription(result: PlanResult): String {
    val tech =
        if (result.techParts.isEmpty()) {
            stringResource(R.string.downloads_original_tracks)
        } else {
            stringResource(R.string.downloads_original_tech, result.techParts.joinToString(", "))
        }
    val minutes =
        result.plan.originalBytes?.let {
            ceil(it / ASSUMED_BYTES_PER_SECOND / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)
        }
    val time = minutes?.let { stringResource(R.string.downloads_original_minutes, it) }
    return listOfNotNull(tech, time).joinToString(" ")
}

private fun shortage(bytes: Long?, free: Long?): Long? {
    if (bytes == null || free == null || bytes <= free) return null
    return bytes - free
}

private val OptionShape = RoundedCornerShape(18.dp)
private const val DISABLED_ALPHA = 0.5f
private const val SHORT_ALPHA = 0.6f
private const val MS_PER_SECOND = 1000.0
private const val SECONDS_PER_MINUTE = 60.0

// 4K HDR to 1080p converted at ~6.6×, 1080p SDR at ~10×; the slow end is used.
private const val CONVERSION_SPEED = 6.0

// The download speed from the server on the home Wi-Fi (~317 Mbit/s measured on the iPhone).
private const val ASSUMED_BYTES_PER_SECOND = 300_000_000.0 / 8
