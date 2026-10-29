package nl.jellycast.mobile.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.mobile.core.remote.PairedTv
import nl.jellycast.mobile.core.remote.PairingTarget
import nl.jellycast.mobile.core.remote.RemoteDevice
import nl.jellycast.mobile.core.remote.RemoteSessions
import nl.jellycast.mobile.core.remote.RemoteState
import nl.jellycast.mobile.core.remote.pairingTarget

data class PlayOnUiState(
    val devices: List<RemoteDevice> = emptyList(),
    val wakeableTvs: List<PairedTv> = emptyList(),
    val tvsToPairAgain: List<PairedTv> = emptyList(),
    /** An Android TV with JellyCast open that can be paired. */
    val pairableTv: PairingTarget? = null,
    val hasUnreachablePairedTv: Boolean = false,
    val paired: Map<String, PairedTv> = emptyMap(),
)

internal fun RemoteState.toPlayOnUiState() = PlayOnUiState(
    devices = playableDevices,
    wakeableTvs = wakeableTvs,
    tvsToPairAgain = tvsToPairAgain,
    pairableTv = pairableTvs.firstNotNullOfOrNull { it.pairingTarget() },
    hasUnreachablePairedTv = hasUnreachablePairedTv,
    paired = paired.associateBy { it.deviceId },
)

/** Where to play: the TVs that take commands, the ones that can be woken, and this phone. */
class PlayOnViewModel(private val remote: RemoteSessions) : ViewModel() {
    val uiState: StateFlow<PlayOnUiState> =
        remote.state
            .map { it.toPlayOnUiState() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MS),
                remote.state.value.toPlayOnUiState(),
            )

    fun onOpen() {
        viewModelScope.launch {
            remote.refresh()
            remote.checkReachability()
        }
    }

    fun onPlay(device: RemoteDevice, itemId: String, startMs: Long, streams: StreamChoice?) =
        remote.launchPlay(device, itemId, startMs, streams)

    fun onWake(tv: PairedTv, itemId: String, startMs: Long, streams: StreamChoice?) =
        remote.launchWakeAndPlay(tv, itemId, startMs, streams)

    fun onRename(deviceId: String, name: String) {
        viewModelScope.launch { remote.rename(deviceId, name) }
    }

    fun onForget(deviceId: String) {
        viewModelScope.launch { remote.forget(deviceId) }
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
