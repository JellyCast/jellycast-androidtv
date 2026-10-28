package nl.jellycast.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.mobile.core.chat.ChatClientFactory
import nl.jellycast.mobile.core.chat.ChatError
import nl.jellycast.mobile.core.chat.ChatException
import nl.jellycast.mobile.core.chat.ChatProvider
import nl.jellycast.mobile.core.chat.ChatSettings
import nl.jellycast.mobile.core.chat.ChatSettingsState

sealed interface KeyCheck {
    data object Idle : KeyCheck

    data object Checking : KeyCheck

    data object Valid : KeyCheck

    data class Failed(val error: ChatError) : KeyCheck
}

data class AiChatSettingsUiState(
    val settings: ChatSettingsState = ChatSettingsState(),
    val check: KeyCheck = KeyCheck.Idle,
)

class AiChatSettingsViewModel(
    private val settings: ChatSettings,
    private val clients: ChatClientFactory,
) : ViewModel() {
    private val check = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    private var checkJob: Job? = null

    val uiState: StateFlow<AiChatSettingsUiState> =
        combine(settings.state, check, ::AiChatSettingsUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, AiChatSettingsUiState())

    fun onProviderChange(provider: ChatProvider) {
        resetCheck()
        viewModelScope.launch { settings.setProvider(provider) }
    }

    /** Saves the key and tests it right away. */
    fun onSaveKey(key: String) {
        if (key.isBlank()) return
        resetCheck()
        checkJob =
            viewModelScope.launch {
                settings.setKey(key)
                testKey()
            }
    }

    fun onTestKey() {
        if (check.value == KeyCheck.Checking) return
        checkJob?.cancel()
        checkJob = viewModelScope.launch { testKey() }
    }

    fun onRemoveKey() {
        resetCheck()
        viewModelScope.launch { settings.setKey(null) }
    }

    fun onSearchesWebChange(searches: Boolean) {
        viewModelScope.launch { settings.setSearchesWeb(searches) }
    }

    fun onAvoidsSpoilersChange(avoids: Boolean) {
        viewModelScope.launch { settings.setAvoidsSpoilers(avoids) }
    }

    private fun resetCheck() {
        checkJob?.cancel()
        check.value = KeyCheck.Idle
    }

    private suspend fun testKey() {
        val client = clients.client() ?: return
        check.value = KeyCheck.Checking
        check.value =
            try {
                client.check()
                KeyCheck.Valid
            } catch (e: CancellationException) {
                check.value = KeyCheck.Idle
                throw e
            } catch (e: ChatException) {
                KeyCheck.Failed(e.error)
            }
    }
}
