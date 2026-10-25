package nl.jellycast.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.AuthButton
import nl.jellycast.tv.core.designsystem.component.AuthContentWidth
import nl.jellycast.tv.core.designsystem.component.AuthHeading
import nl.jellycast.tv.core.designsystem.component.AuthLayout
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.model.AppLanguage
import nl.jellycast.tv.core.model.MovieView
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onChangeServer: () -> Unit,
    onOpenPlayback: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onChangeServer = onChangeServer,
        onOpenPlayback = onOpenPlayback,
        onLogout = viewModel::onLogout,
        onMaxBitrateClick = viewModel::onMaxBitrateClick,
        onBurnInAssToggle = viewModel::onBurnInAssToggle,
        onMovieViewToggle = viewModel::onMovieViewToggle,
        onLanguageClick = viewModel::onLanguageClick,
        modifier = modifier,
    )
}

private enum class Explained { None, Language, MovieView, MaxBitrate, BurnInAss, Playback }

private class SettingsFocus(
    val first: FocusRequester = FocusRequester(),
    val changeServer: FocusRequester = FocusRequester(),
    val playback: FocusRequester = FocusRequester(),
)

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onChangeServer: () -> Unit,
    onOpenPlayback: () -> Unit,
    onLogout: () -> Unit,
    onMaxBitrateClick: () -> Unit,
    onBurnInAssToggle: () -> Unit,
    onMovieViewToggle: () -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { SettingsFocus() }
    var openedServer by rememberSaveable { mutableStateOf(false) }
    var openedPlayback by rememberSaveable { mutableStateOf(false) }
    // A language change recreates the Activity; the language button is first, so focus lands there.
    LaunchedEffect(Unit) {
        when {
            openedServer -> focus.changeServer.requestFocus()
            openedPlayback -> focus.playback.requestFocus()
            else -> focus.first.requestFocus()
        }
    }
    val explanation = remember { ExplanationState(Explained.None) }

    AuthLayout(
        modifier = modifier,
        spacing = SettingsSpacing,
        footerReserve = FooterReserve,
        footer = { SettingsFooter(uiState = uiState, explanation = explanation) },
    ) {
        DisplayGroup(
            uiState = uiState,
            onLanguageClick = onLanguageClick,
            onMovieViewToggle = onMovieViewToggle,
            focus = focus,
            explanation = explanation,
        )
        PlaybackGroup(
            uiState = uiState,
            onMaxBitrateClick = onMaxBitrateClick,
            onBurnInAssToggle = onBurnInAssToggle,
            onOpenPlayback = {
                openedPlayback = true
                onOpenPlayback()
            },
            focus = focus,
            explanation = explanation,
        )
        AccountGroup(
            uiState = uiState,
            onChangeServer = {
                openedServer = true
                onChangeServer()
            },
            onLogout = onLogout,
            focus = focus,
        )
    }
}

@Composable
private fun SettingsFooter(uiState: SettingsUiState, explanation: ExplanationState<Explained>) {
    Column(
        modifier = Modifier.widthIn(max = FooterWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = explanation.explained.text(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    R.string.settings_version,
                    uiState.versionName,
                    uiState.versionCode,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.DisplayGroup(
    uiState: SettingsUiState,
    onLanguageClick: () -> Unit,
    onMovieViewToggle: () -> Unit,
    focus: SettingsFocus,
    explanation: ExplanationState<Explained>,
) {
    val buttonModifier = Modifier.align(Alignment.CenterHorizontally)
    AuthHeading(stringResource(R.string.settings_group_display))
    val language = uiState.language
    if (language != null) {
        AuthButton(
            text = stringResource(R.string.settings_language, language.name),
            onClick = onLanguageClick,
            modifier =
                buttonModifier
                    .focusRequester(focus.first)
                    .explains(explanation, Explained.Language),
        )
    }
    val movieViewFocus =
        if (language == null) Modifier.focusRequester(focus.first) else Modifier
    AuthButton(
        text =
            stringResource(
                when (uiState.movieView) {
                    MovieView.Detail -> R.string.settings_movie_view_detail
                    MovieView.Bookcase -> R.string.settings_movie_view_bookcase
                },
            ),
        onClick = onMovieViewToggle,
        modifier =
            buttonModifier
                .then(movieViewFocus)
                .explains(explanation, Explained.MovieView),
    )
}

@Composable
private fun ColumnScope.PlaybackGroup(
    uiState: SettingsUiState,
    onMaxBitrateClick: () -> Unit,
    onBurnInAssToggle: () -> Unit,
    onOpenPlayback: () -> Unit,
    focus: SettingsFocus,
    explanation: ExplanationState<Explained>,
) {
    val buttonModifier = Modifier.align(Alignment.CenterHorizontally)
    GroupHeading(stringResource(R.string.settings_group_playback))
    AuthButton(
        text =
            uiState.maxBitrateMbps?.let { stringResource(R.string.settings_max_bitrate, it) }
                ?: stringResource(R.string.settings_max_bitrate_none),
        onClick = onMaxBitrateClick,
        modifier = buttonModifier.explains(explanation, Explained.MaxBitrate),
    )
    AuthButton(
        text =
            stringResource(
                if (uiState.burnInAss) {
                    R.string.settings_burn_in_ass_on
                } else {
                    R.string.settings_burn_in_ass_off
                },
            ),
        onClick = onBurnInAssToggle,
        modifier = buttonModifier.explains(explanation, Explained.BurnInAss),
    )
    AuthButton(
        text = stringResource(R.string.settings_playback),
        onClick = onOpenPlayback,
        modifier =
            buttonModifier
                .focusRequester(focus.playback)
                .explains(explanation, Explained.Playback),
    )
}

@Composable
private fun ColumnScope.AccountGroup(
    uiState: SettingsUiState,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
    focus: SettingsFocus,
) {
    val buttonModifier = Modifier.align(Alignment.CenterHorizontally)
    val logoutLabel =
        if (uiState.isLoggingOut) R.string.settings_logging_out else R.string.settings_logout
    GroupHeading(stringResource(R.string.settings_group_account))
    Text(
        text = stringResource(R.string.settings_account, uiState.userName, uiState.serverName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(AuthContentWidth),
    )
    AuthButton(
        text = stringResource(R.string.settings_change_server),
        onClick = onChangeServer,
        modifier = buttonModifier.focusRequester(focus.changeServer),
    )
    AuthButton(
        text = stringResource(logoutLabel),
        onClick = onLogout,
        modifier = buttonModifier,
    )
}

@Composable
private fun GroupHeading(text: String) {
    AuthHeading(text, modifier = Modifier.padding(top = GroupHeadingTopPadding))
}

@Composable
private fun Explained.text(): String = when (this) {
    Explained.None -> ""
    Explained.Language -> stringResource(R.string.settings_language_explain)
    Explained.MovieView -> stringResource(R.string.settings_movie_view_explain)
    Explained.MaxBitrate -> stringResource(R.string.settings_max_bitrate_explain)
    Explained.BurnInAss -> stringResource(R.string.settings_burn_in_ass_explain)
    Explained.Playback -> stringResource(R.string.settings_playback_explain)
}

// Tighter than the login screens: more buttons, and everything has to fit in 540 dp.
private val SettingsSpacing = 8.dp

// Room for about four lines of explanation plus the version line below the brand.
private val FooterReserve = 180.dp

// Ends before the button column, which starts at 520 dp.
private val FooterWidth = 400.dp
private val GroupHeadingTopPadding = 10.dp

@Preview(device = Devices.TV_1080p)
@Composable
private fun SettingsContentPreview() {
    JellyCastTheme {
        SettingsContent(
            uiState =
                SettingsUiState(
                    versionName = "0.1.0",
                    versionCode = 1,
                    serverName = "home server",
                    userName = "claude",
                    maxBitrateMbps = 40,
                    language = AppLanguage("nl", "Nederlands"),
                ),
            onChangeServer = {},
            onOpenPlayback = {},
            onLogout = {},
            onMaxBitrateClick = {},
            onBurnInAssToggle = {},
            onMovieViewToggle = {},
            onLanguageClick = {},
        )
    }
}
