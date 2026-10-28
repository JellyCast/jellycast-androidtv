package nl.jellycast.core.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * After a display mode switch HDCP needs over 2 s to renegotiate. Audio that starts in that window
 * stutters and gets cut by the HDCP switch.
 */
const val DISPLAY_SETTLE_MS = 2_500L

const val DISPLAY_SWITCH_TIMEOUT_MS = 3_000L

/** An amplifier or soundbar drops the audio output during the resync; wait until it is quiet. */
const val AUDIO_ROUTE_QUIET_MS = 1_000L

const val DISPLAY_SETTLE_MAX_MS = 6_000L

val StartDelayOptions = listOf(0, 500, 1_000, 2_000)

private sealed interface SettleEvent {
    data class Mode(val id: Int) : SettleEvent

    data class Route(val available: Boolean) : SettleEvent
}

/**
 * `true` once audio may start:
 * - without a mode switch ([targetModeId] `null`) after [extraDelayMs];
 * - with a switch once [targetModeId] has been active for [settleMs] + [extraDelayMs] and the
 *   audio output ([audioRoute]: `false` = dropped) is back and quiet for [routeQuietMs], capped at
 *   [maxWaitMs] (+ [extraDelayMs]) after the new mode;
 * - after [timeoutMs] when a different mode stays active.
 *
 * A later mode change goes back to `false`; route changes after the start are ignored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun displaySettled(
    targetModeId: Int?,
    activeModeIds: Flow<Int>,
    audioRoute: Flow<Boolean> = emptyFlow(),
    extraDelayMs: Long = 0,
    settleMs: Long = DISPLAY_SETTLE_MS,
    timeoutMs: Long = DISPLAY_SWITCH_TIMEOUT_MS,
    routeQuietMs: Long = AUDIO_ROUTE_QUIET_MS,
    maxWaitMs: Long = DISPLAY_SETTLE_MAX_MS,
    now: () -> Long = System::currentTimeMillis,
): Flow<Boolean> {
    if (targetModeId == null) {
        return flow {
            if (extraDelayMs > 0) {
                emit(false)
                delay(extraDelayMs)
            }
            emit(true)
        }
    }
    var modeActiveAt: Long? = null
    var otherModeAt = 0L
    var lastRouteAt: Long? = null
    var routeAvailable = true
    var seenMode = false
    var settled = false
    val events =
        merge(
            activeModeIds.distinctUntilChanged().map { SettleEvent.Mode(it) },
            audioRoute.map { SettleEvent.Route(it) },
        )
    return events
        .transformLatest { event ->
            when (event) {
                is SettleEvent.Mode -> {
                    seenMode = true
                    settled = false
                    if (event.id == targetModeId) {
                        modeActiveAt = now()
                    } else {
                        modeActiveAt = null
                        otherModeAt = now()
                    }
                }

                is SettleEvent.Route -> {
                    if (settled) return@transformLatest
                    routeAvailable = event.available
                    lastRouteAt = now()
                    if (!seenMode) return@transformLatest
                }
            }
            emit(false)
            val activeAt = modeActiveAt
            val deadline =
                if (activeAt != null) {
                    val latest = activeAt + maxWaitMs
                    val quiet =
                        when {
                            !routeAvailable -> latest
                            else -> lastRouteAt?.let { it + routeQuietMs } ?: Long.MIN_VALUE
                        }
                    minOf(maxOf(activeAt + settleMs, quiet), latest) + extraDelayMs
                } else {
                    otherModeAt + timeoutMs
                }
            delay((deadline - now()).coerceAtLeast(0))
            settled = true
            emit(true)
        }.distinctUntilChanged()
}

/**
 * `true` once the video decoder may start, and it stays `true`. A hardware decoder that already
 * outputs frames during the HDMI resync sometimes shows green and blue blocks until restarted.
 */
fun displayModeReached(
    targetModeId: Int?,
    activeModeIds: Flow<Int>,
    timeoutMs: Long = DISPLAY_SWITCH_TIMEOUT_MS,
): Flow<Boolean> = flow {
    if (targetModeId != null) {
        emit(false)
        withTimeoutOrNull(timeoutMs) { activeModeIds.first { it == targetModeId } }
    }
    emit(true)
}
