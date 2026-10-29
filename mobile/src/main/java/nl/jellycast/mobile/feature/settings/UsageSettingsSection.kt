package nl.jellycast.mobile.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.UsageRepository
import nl.jellycast.core.model.UsageChoice
import nl.jellycast.mobile.R
import nl.jellycast.mobile.ui.usage.UsageStatisticsDetails
import org.koin.compose.viewmodel.koinViewModel

class UsageSettingsViewModel(private val usage: UsageRepository) : ViewModel() {
    val isAllowed: StateFlow<Boolean> =
        usage.choice
            .map { it == UsageChoice.Allowed }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onAllowedChange(allowed: Boolean) {
        viewModelScope.launch {
            usage.choose(if (allowed) UsageChoice.Allowed else UsageChoice.Declined)
        }
    }
}

@Composable
internal fun UsageSettingsSection(viewModel: UsageSettingsViewModel = koinViewModel()) {
    val isAllowed by viewModel.isAllowed.collectAsStateWithLifecycle()
    var showsDetails by rememberSaveable { mutableStateOf(false) }
    SettingsSection(
        title = stringResource(R.string.settings_privacy),
        footer = stringResource(R.string.settings_usage_footer),
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_usage),
            checked = isAllowed,
            onCheckedChange = viewModel::onAllowedChange,
        )
        SettingsRow(
            title = stringResource(R.string.settings_usage_details),
            onClick = { showsDetails = !showsDetails },
        )
        if (showsDetails) {
            UsageStatisticsDetails(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}
