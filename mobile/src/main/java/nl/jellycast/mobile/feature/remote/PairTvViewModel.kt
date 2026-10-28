package nl.jellycast.mobile.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.mobile.core.remote.PairedTv
import nl.jellycast.mobile.core.remote.RemoteDevice
import nl.jellycast.mobile.core.remote.RemoteSessions
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvPairing
import nl.jellycast.mobile.core.remote.androidtv.PairingException
import nl.jellycast.mobile.core.remote.androidtv.RemoteClientInfo

enum class PairingFailure { NoAnswer, WrongCode, Failed }

sealed interface PairingPhase {
    data object Connecting : PairingPhase

    data object EnterCode : PairingPhase

    data object Pairing : PairingPhase

    data class Failed(val reason: PairingFailure) : PairingPhase

    data object Paired : PairingPhase
}

data class PairTvUiState(val phase: PairingPhase = PairingPhase.Connecting, val code: String = "") {
    val canPair: Boolean get() = phase == PairingPhase.EnterCode && code.length == CODE_LENGTH

    companion object {
        const val CODE_LENGTH = 6
    }
}

/** One-time pairing with a Chromecast whose JellyCast is open: the TV shows a code to type. */
class PairTvViewModel(
    private val pairing: AndroidTvPairing,
    private val remote: RemoteSessions,
    private val client: RemoteClientInfo,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PairTvUiState())
    val uiState: StateFlow<PairTvUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var code: CompletableDeferred<String>? = null

    fun onStart(device: RemoteDevice) {
        val host = device.address ?: return
        job?.cancel()
        val entered = CompletableDeferred<String>()
        code = entered
        _uiState.value = PairTvUiState()
        job =
            viewModelScope.launch {
                val phase =
                    try {
                        pairing.pair(
                            host,
                            client.pairingName,
                            onCodeShown = { setPhase(PairingPhase.EnterCode) },
                            awaitCode = { entered.await() },
                        )
                        remote.addPaired(PairedTv(device.deviceId, host, device.name))
                        PairingPhase.Paired
                    } catch (_: PairingException.WrongCode) {
                        PairingPhase.Failed(PairingFailure.WrongCode)
                    } catch (_: PairingException.Refused) {
                        PairingPhase.Failed(PairingFailure.Failed)
                    } catch (_: IOException) {
                        failureBeforeOrAfterCode()
                    } catch (_: GeneralSecurityException) {
                        failureBeforeOrAfterCode()
                    }
                setPhase(phase)
            }
    }

    fun onCodeChange(text: String) {
        val cleaned =
            text.uppercase().filter {
                it.isDigit() || it in 'A'..'F'
            }.take(PairTvUiState.CODE_LENGTH)
        _uiState.update { it.copy(code = cleaned) }
    }

    fun onPair() {
        val state = _uiState.value
        if (!state.canPair) return
        setPhase(PairingPhase.Pairing)
        code?.complete(state.code)
    }

    fun onCancel() {
        job?.cancel()
        job = null
    }

    private fun failureBeforeOrAfterCode(): PairingPhase = PairingPhase.Failed(
        if (_uiState.value.phase == PairingPhase.Connecting) {
            PairingFailure.NoAnswer
        } else {
            PairingFailure.Failed
        },
    )

    private fun setPhase(phase: PairingPhase) {
        _uiState.update { it.copy(phase = phase) }
    }
}
