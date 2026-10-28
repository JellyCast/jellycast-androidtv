package nl.jellycast.tv.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.jellycast.core.model.HomeSection
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * [takeFocus]: once its data is loaded, the section focuses its start point (or the item from
 * before Back) and reports it with [onTakeFocusComplete]. Only true when Home opens, never on a
 * section switch. [onBackToBar]: the section has reset itself to its start point; focus the bar.
 */
typealias SectionContent =
    @Composable (
        section: HomeSection,
        takeFocus: Boolean,
        onTakeFocusComplete: () -> Unit,
        onBackToBar: () -> Unit,
    ) -> Unit

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    sectionContent: SectionContent,
    modifier: Modifier = Modifier,
    barAccessory: @Composable (HomeSection) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        onSelectSection = viewModel::onSelectSection,
        onOpenSettings = onOpenSettings,
        sectionContent = sectionContent,
        barAccessory = barAccessory,
        modifier = modifier,
    )
}

private enum class FocusArea { Content, Tabs, Settings }

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onSelectSection: (HomeSection) -> Unit,
    onOpenSettings: () -> Unit,
    sectionContent: SectionContent,
    modifier: Modifier = Modifier,
    barAccessory: @Composable (HomeSection) -> Unit = {},
) {
    val section = uiState.section
    var focusArea by rememberSaveable { mutableStateOf(FocusArea.Content) }
    // Not saveable: re-evaluated every time Home appears.
    var contentTakesFocus by remember { mutableStateOf(focusArea == FocusArea.Content) }
    val tabRequesters = remember { HomeSection.entries.associateWith { FocusRequester() } }
    val settingsRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        when (focusArea) {
            FocusArea.Tabs -> tabRequesters.getValue(section).requestFocus()
            FocusArea.Settings -> settingsRequester.requestFocus()
            FocusArea.Content -> Unit
        }
    }

    BackHandler(enabled = focusArea == FocusArea.Content) {
        tabRequesters.getValue(section).requestFocus()
    }

    var exitArmed by remember { mutableStateOf(false) }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(EXIT_CONFIRM_WINDOW_MS)
            exitArmed = false
        }
    }
    BackHandler(enabled = focusArea != FocusArea.Content && !exitArmed) {
        if (focusArea == FocusArea.Tabs && section == HomeSection.Home) {
            exitArmed = true
        } else {
            tabRequesters.getValue(HomeSection.Home).requestFocus()
        }
    }

    // No background of its own: the window already has it, and another full-screen layer costs
    // GPU time every frame on the Chromecast.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown) {
                            contentTakesFocus = false
                            if (it.key != Key.Back) exitArmed = false
                        }
                        false
                    },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onFocusChanged { if (it.hasFocus) focusArea = FocusArea.Content },
            ) {
                sectionContent(
                    section,
                    contentTakesFocus,
                    { contentTakesFocus = false },
                    { tabRequesters.getValue(section).requestFocus() },
                )
            }
            SectionBar(
                selected = section,
                tabRequesters = tabRequesters,
                settingsRequester = settingsRequester,
                onSelectSection = onSelectSection,
                onOpenSettings = onOpenSettings,
                onTabsFocus = { focusArea = FocusArea.Tabs },
                onSettingsFocus = { focusArea = FocusArea.Settings },
                accessory = { barAccessory(section) },
            )
            if (exitArmed) ExitHint(Modifier.align(Alignment.BottomCenter))
        }
    }
}

private const val EXIT_CONFIRM_WINDOW_MS = 2_000L

@Composable
private fun ExitHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.home_exit_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .padding(bottom = Dimens.SafeVertical)
                .background(MaterialTheme.colorScheme.surface, Dimens.PillShape)
                .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeContentPreview() {
    JellyCastTheme {
        HomeContent(
            uiState = HomeUiState(HomeSection.Home),
            onSelectSection = {},
            onOpenSettings = {},
            sectionContent = { _, _, _, _ -> },
        )
    }
}
