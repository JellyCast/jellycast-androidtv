package nl.jellycast.tv.core.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisplaySettleTest {
    private val now60Hz = 583
    private val target24Hz = 589

    @Test
    fun `without a mode switch playback starts at once`() = runTest {
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(targetModeId = null, activeModeIds = emptyFlow()).collect {
                values += it
            }
        }
        assertEquals(listOf(true), values)
    }

    @Test
    fun `waits for the new mode and then the HDMI resync`() = runTest {
        val active = MutableStateFlow(now60Hz)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(target24Hz, active, settleMs = 2_500, timeoutMs = 3_000, now = {
                testScheduler.currentTime
            })
                .collect { values += it }
        }
        advanceTimeBy(500)
        active.value = target24Hz
        advanceTimeBy(2_499)
        assertEquals(listOf(false), values)

        advanceTimeBy(2)
        assertEquals(listOf(false, true), values)
    }

    @Test
    fun `plays after the timeout when the mode never comes`() = runTest {
        val active = MutableStateFlow(now60Hz)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(target24Hz, active, settleMs = 2_500, timeoutMs = 3_000, now = {
                testScheduler.currentTime
            })
                .collect { values += it }
        }
        advanceTimeBy(3_001)
        assertEquals(listOf(false, true), values)
    }

    @Test
    fun `a later switch makes it wait again`() = runTest {
        val active = MutableStateFlow(target24Hz)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(target24Hz, active, settleMs = 2_500, timeoutMs = 3_000, now = {
                testScheduler.currentTime
            })
                .collect { values += it }
        }
        advanceTimeBy(2_501)
        // Background and back: the system sets 60 Hz and then 23.976 Hz again.
        active.value = now60Hz
        runCurrent()
        active.value = target24Hz
        advanceTimeBy(2_501)
        assertEquals(listOf(false, true, false, true), values)
    }

    @Test
    fun `waits until an audio output that drops during the resync is quiet`() = runTest {
        val active = MutableStateFlow(now60Hz)
        val routes = MutableSharedFlow<Boolean>()
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(
                target24Hz,
                active,
                audioRoute = routes,
                now = { testScheduler.currentTime },
            ).collect { values += it }
        }
        active.value = target24Hz
        // Amplifier: HDMI audio drops after 1.8 s and returns after 3.2 s.
        advanceTimeBy(1_800)
        routes.emit(false)
        advanceTimeBy(1_400)
        routes.emit(true)
        advanceTimeBy(999)
        assertEquals(listOf(false), values)

        advanceTimeBy(2)
        assertEquals(listOf(false, true), values)
    }

    @Test
    fun `an output that keeps changing delays the start no longer than the maximum`() = runTest {
        val active = MutableStateFlow(target24Hz)
        val routes = MutableSharedFlow<Boolean>()
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(
                target24Hz,
                active,
                audioRoute = routes,
                now = { testScheduler.currentTime },
            ).collect { values += it }
        }
        repeat(12) {
            advanceTimeBy(500)
            routes.emit(true)
        }
        advanceTimeBy(1)
        assertEquals(listOf(false, true), values)
    }

    @Test
    fun `the start delay adds to the wait, also without a mode switch`() = runTest {
        val active = MutableStateFlow(target24Hz)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(target24Hz, active, extraDelayMs = 1_000, now = {
                testScheduler.currentTime
            })
                .collect { values += it }
        }
        advanceTimeBy(3_499)
        assertEquals(listOf(false), values)
        advanceTimeBy(2)
        assertEquals(listOf(false, true), values)

        val noSwitch = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(null, emptyFlow(), extraDelayMs = 500).collect { noSwitch += it }
        }
        advanceTimeBy(501)
        assertEquals(listOf(false, true), noSwitch)
    }

    @Test
    fun `an output change after the start is ignored`() = runTest {
        val active = MutableStateFlow(target24Hz)
        val routes = MutableSharedFlow<Boolean>()
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displaySettled(target24Hz, active, audioRoute = routes, now = {
                testScheduler.currentTime
            })
                .collect { values += it }
        }
        advanceTimeBy(2_501)
        routes.emit(false)
        advanceTimeBy(5_000)
        assertEquals(listOf(false, true), values)
    }

    @Test
    fun `without a mode switch the decoder starts at once`() = runTest {
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displayModeReached(targetModeId = null, activeModeIds = emptyFlow()).collect {
                values += it
            }
        }
        assertEquals(listOf(true), values)
    }

    @Test
    fun `the decoder starts only once the new mode is active`() = runTest {
        val active = MutableStateFlow(now60Hz)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displayModeReached(target24Hz, active, timeoutMs = 3_000).collect { values += it }
        }
        advanceTimeBy(500)
        assertEquals(listOf(false), values)

        active.value = target24Hz
        runCurrent()
        assertEquals(listOf(false, true), values)

        // A later switch does not restart the decoder.
        active.value = now60Hz
        runCurrent()
        assertEquals(listOf(false, true), values)
    }

    @Test
    fun `the decoder starts after the timeout when the mode never comes`() = runTest {
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            displayModeReached(target24Hz, MutableStateFlow(now60Hz), timeoutMs = 3_000)
                .collect { values += it }
        }
        advanceTimeBy(2_999)
        assertEquals(listOf(false), values)

        advanceTimeBy(2)
        assertEquals(listOf(false, true), values)
    }
}
