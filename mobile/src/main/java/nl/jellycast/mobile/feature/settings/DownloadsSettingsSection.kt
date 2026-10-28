package nl.jellycast.mobile.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.downloads.DownloadSettings
import org.koin.compose.koinInject

@Composable
internal fun DownloadsSettingsSection(settings: DownloadSettings = koinInject()) {
    val removesWatched by settings.removesWatched.collectAsStateWithLifecycle()
    val keepsAhead by settings.keepsSeriesAhead.collectAsStateWithLifecycle()
    SettingsSection(
        title = stringResource(R.string.settings_downloads),
        footer = stringResource(R.string.settings_downloads_footer, DownloadSettings.SERIES_BATCH),
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_downloads_remove_watched),
            checked = removesWatched,
            onCheckedChange = settings::setRemovesWatched,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_downloads_keep_ahead),
            checked = keepsAhead,
            onCheckedChange = settings::setKeepsSeriesAhead,
        )
    }
}
