package nl.jellycast.mobile.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.core.settings.QualityLevel
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.core.settings.forNetwork

/** Server, account, library, streaming quality, ratings and AI chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onRefreshLibrary: () -> Unit,
    onQualityChange: (NetworkKind, QualityPreference) -> Unit,
    onRatingSourceChange: (RatingSource) -> Unit,
    onOpenAiChat: () -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JellyCastColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(R.drawable.ic_settings_close),
                            contentDescription = stringResource(R.string.settings_done),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JellyCastColors.Background),
            )
        },
    ) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            ServerSection(uiState)
            LibrarySection(uiState, onRefreshLibrary)
            QualitySection(uiState, onQualityChange)
            RatingSection(uiState.ratingSource, onRatingSourceChange)
            SettingsSection {
                SettingsRow(
                    title = stringResource(R.string.settings_ai_chat),
                    value =
                        if (uiState.chat.hasKey) {
                            uiState.chat.provider.title
                        } else {
                            stringResource(R.string.settings_off)
                        },
                    onClick = onOpenAiChat,
                )
            }
            SettingsSection {
                SettingsRow(
                    title = stringResource(R.string.settings_sign_out),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = onSignOut,
                )
            }
            Text(
                stringResource(R.string.settings_version, uiState.versionName, uiState.versionCode),
                style = MaterialTheme.typography.bodySmall,
                color = JellyCastColors.TextMuted,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
    }
}

/** Scrolls, keeps 16 side margins and stays readable on a tablet. */
@Composable
internal fun SettingsColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) { content() }
    }
}

@Composable
private fun ServerSection(uiState: SettingsUiState) {
    SettingsSection(title = stringResource(R.string.settings_server)) {
        SettingsRow(stringResource(R.string.settings_server), value = uiState.serverName)
        SettingsRow(stringResource(R.string.settings_address), supporting = uiState.serverUrl)
        SettingsRow(stringResource(R.string.settings_signed_in_as), value = uiState.userName)
    }
}

@Composable
private fun LibrarySection(uiState: SettingsUiState, onRefreshLibrary: () -> Unit) {
    val syncing = uiState.syncState == SyncState.Syncing
    SettingsSection(title = stringResource(R.string.settings_library)) {
        uiState.library?.let { library ->
            val numbers = NumberFormat.getIntegerInstance()
            SettingsRow(stringResource(R.string.settings_movies), value = numbers.format(library.movies))
            SettingsRow(stringResource(R.string.settings_series), value = numbers.format(library.series))
            SettingsRow(stringResource(R.string.settings_episodes), value = numbers.format(library.episodes))
            SettingsRow(
                stringResource(R.string.settings_updated),
                value =
                    DateUtils
                        .getRelativeTimeSpanString(
                            library.syncedAtMs,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ).toString(),
            )
        }
        (uiState.syncState as? SyncState.Failed)?.message?.let { message ->
            Text(
                message,
                color = JellyCastColors.Warning,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        SettingsRow(
            title = stringResource(R.string.settings_refresh_library),
            titleColor = if (syncing) JellyCastColors.TextMuted else JellyCastColors.Accent,
            enabled = !syncing,
            onClick = onRefreshLibrary,
            trailing =
                if (syncing) {
                    { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
        )
    }
}

private val QualityNetworks = listOf(NetworkKind.Local, NetworkKind.Wifi, NetworkKind.Cellular)

@Composable
private fun QualitySection(
    uiState: SettingsUiState,
    onQualityChange: (NetworkKind, QualityPreference) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<NetworkKind?>(null) }
    SettingsSection(
        title = stringResource(R.string.settings_streaming_quality),
        footer = stringResource(R.string.settings_quality_footer),
    ) {
        QualityNetworks.forEach { network ->
            SettingsRow(
                title = networkName(network),
                value = qualityLabel(uiState.quality.forNetwork(network)),
                onClick = { picking = network },
            )
        }
        SettingsRow(stringResource(R.string.settings_now_on), value = networkName(uiState.networkKind))
    }
    picking?.let { network ->
        val options = QualityPreference.all
        ChoiceDialog(
            title = networkName(network),
            options = options.map { qualityLabel(it) },
            selectedIndex = options.indexOf(uiState.quality.forNetwork(network)),
            onSelect = { index ->
                onQualityChange(network, options[index])
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun networkName(network: NetworkKind): String = when (network) {
    NetworkKind.Local -> stringResource(R.string.settings_network_local)
    NetworkKind.Wifi -> stringResource(R.string.settings_network_wifi)
    NetworkKind.Cellular -> stringResource(R.string.settings_network_cellular)
    NetworkKind.None -> stringResource(R.string.settings_network_none)
}

@Composable
internal fun qualityLabel(preference: QualityPreference): String = when (preference) {
    is QualityPreference.Auto ->
        if (preference.upTo == QualityLevel.Original) {
            stringResource(R.string.settings_quality_auto)
        } else {
            stringResource(R.string.settings_quality_auto_up_to, preference.upTo.shortName)
        }

    is QualityPreference.Fixed -> levelLabel(preference.level)
}

/** "5 Mbit/s · 1080p"; Original is the file as it is. */
@Composable
private fun levelLabel(level: QualityLevel): String {
    val bitrate = level.maxBitrate ?: return stringResource(R.string.settings_quality_original)
    val megabits =
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
            .format(bitrate / BITS_PER_MEGABIT)
    return stringResource(R.string.settings_quality_level, megabits, level.shortName)
}

private const val BITS_PER_MEGABIT = 1_000_000.0

@Composable
private fun RatingSection(source: RatingSource, onRatingSourceChange: (RatingSource) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsSection(
        title = stringResource(R.string.settings_ratings),
        footer =
            stringResource(
                when (source) {
                    RatingSource.Imdb -> R.string.settings_ratings_footer_imdb
                    RatingSource.RottenTomatoes -> R.string.settings_ratings_footer_rotten_tomatoes
                    RatingSource.Tmdb -> R.string.settings_ratings_footer_tmdb
                },
            ),
    ) {
        SettingsRow(
            title = stringResource(R.string.settings_rating_and_links),
            value = source.siteName,
            onClick = { picking = true },
        )
    }
    if (picking) {
        ChoiceDialog(
            title = stringResource(R.string.settings_rating_and_links),
            options = RatingSource.entries.map { it.siteName },
            selectedIndex = RatingSource.entries.indexOf(source),
            onSelect = { index ->
                onRatingSourceChange(RatingSource.entries[index])
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Preview
@Composable
private fun SettingsContentPreview() {
    JellyCastTheme {
        SettingsContent(
            uiState =
                SettingsUiState(
                    serverName = "home server",
                    serverUrl = "http://192.168.1.4:8096",
                    userName = "han",
                    library = LibraryCounts(412, 58, 2310, 0),
                    quality = NetworkKind.entries.associateWith(QualityPreference::defaultFor),
                    versionName = "0.1.0",
                    versionCode = 1,
                ),
            onRefreshLibrary = {},
            onQualityChange = { _, _ -> },
            onRatingSourceChange = {},
            onOpenAiChat = {},
            onSignOut = {},
            onClose = {},
        )
    }
}
