package nl.jellycast.mobile.core.remote

import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.mobile.testing.FakeAndroidTvLink
import nl.jellycast.mobile.testing.FakeRemoteDeviceStore
import nl.jellycast.mobile.testing.FakeRemoteSessionsApi
import nl.jellycast.mobile.testing.testRemoteDevice
import nl.jellycast.mobile.testing.testRemoteSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSessionsTest {
    private val api = FakeRemoteSessionsApi()
    private val deviceStore = FakeRemoteDeviceStore()
    private val link = FakeAndroidTvLink()
    private var nowMs = 0L

    private fun TestScope.remote() =
        RemoteSessions(api, deviceStore, link, Clock { nowMs }, backgroundScope)

    @Test
    fun `a refresh shows what plays, with the names given on this phone`() = runTest {
        deviceStore.saved = SavedRemoteDevices(names = mapOf("device-1" to "Living room"))
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession()), listOf(testRemoteDevice()))
        val remote = remote()

        remote.refresh()

        assertEquals("Living room", remote.state.value.current?.deviceName)
        assertEquals("Living room", remote.state.value.devices.single().name)
    }

    @Test
    fun `a session that stopped reporting is gone, and so is a device without recent activity`() =
        runTest {
            nowMs = 200_000
            api.snapshot =
                RemoteSnapshot(
                    listOf(testRemoteSession(checkedInAtMs = 100_000)),
                    listOf(testRemoteDevice(lastActivityMs = 0)),
                )
            val remote = remote()

            remote.refresh()

            assertNull(remote.state.value.current)
            assertTrue(remote.state.value.devices.isEmpty())
        }

    @Test
    fun `one failed poll keeps the bar, three clear it`() = runTest {
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession()), emptyList())
        val remote = remote()
        remote.refresh()
        api.failSnapshots = true

        remote.refresh()
        remote.refresh()
        assertEquals("session-1", remote.state.value.current?.id)
        remote.refresh()

        assertNull(remote.state.value.current)
    }

    @Test
    fun `a pause shows at once, at the position it had reached`() = runTest {
        nowMs = 10_000
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession(checkedInAtMs = 0)), emptyList())
        val remote = remote()
        remote.refresh()
        api.snapshot = RemoteSnapshot(emptyList(), emptyList())
        api.failSnapshots = true

        backgroundScope.launch { remote.send(RemoteCommand.Pause, "session-1") }
        runCurrent()

        val session = remote.state.value.current
        assertEquals(true, session?.isPaused)
        assertEquals(70_000L, session?.positionMs)
        assertEquals(listOf(RemoteCommand.Pause to "session-1"), api.sent)
    }

    @Test
    fun `a refused command shows a failure`() = runTest {
        api.failCommands = true
        val remote = remote()

        remote.send(RemoteCommand.Stop, "session-1")

        assertTrue(remote.state.value.commandFailed)
        remote.dismissCommandFailure()
        assertFalse(remote.state.value.commandFailed)
    }

    @Test
    fun `the session follows its TV when the next episode plays under a new session`() = runTest {
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession(id = "first")), emptyList())
        val remote = remote()
        remote.refresh()
        remote.show(remote.state.value.sessions.single())
        api.snapshot = RemoteSnapshot(listOf(testRemoteSession(id = "second")), emptyList())

        remote.refresh()

        assertEquals("second", remote.state.value.shownSessionId)
    }

    @Test
    fun `a paired TV moves with the address its session reports`() = runTest {
        deviceStore.saved =
            SavedRemoteDevices(paired = listOf(PairedTv("device-1", "10.0.0.1", "TV")))
        api.snapshot = RemoteSnapshot(emptyList(), listOf(testRemoteDevice(address = "10.0.0.9")))
        val remote = remote()

        remote.refresh()

        assertEquals("10.0.0.9", deviceStore.saved.paired.single().host)
    }

    @Test
    fun `a sleeping paired TV can be woken once the probe reached it`() = runTest {
        deviceStore.saved =
            SavedRemoteDevices(paired = listOf(PairedTv("device-1", "10.0.0.1", "TV")))
        val remote = remote()
        remote.refresh()
        assertTrue(remote.state.value.wakeableTvs.isEmpty())
        link.reachableHosts += "10.0.0.1"

        remote.checkReachability()

        assertEquals(listOf("device-1"), remote.state.value.wakeableTvs.map { it.deviceId })
        assertFalse(remote.state.value.hasUnreachablePairedTv)
    }

    @Test
    fun `an unreachable paired TV is only mentioned, not offered`() = runTest {
        deviceStore.saved =
            SavedRemoteDevices(paired = listOf(PairedTv("device-1", "10.0.0.1", "TV")))
        api.snapshot = RemoteSnapshot(emptyList(), listOf(testRemoteDevice()))
        val remote = remote()
        remote.refresh()

        remote.checkReachability()

        assertTrue(remote.state.value.playableDevices.isEmpty())
        assertTrue(remote.state.value.hasUnreachablePairedTv)
    }

    @Test
    fun `an open unpaired Android TV can be paired`() = runTest {
        api.snapshot =
            RemoteSnapshot(
                emptyList(),
                listOf(
                    testRemoteDevice(),
                    testRemoteDevice(id = "s2", deviceId = "webos-1", isAndroidTv = false),
                ),
            )
        val remote = remote()
        remote.refresh()

        assertEquals(listOf("device-1"), remote.state.value.pairableTvs.map { it.deviceId })
        remote.addPaired(PairedTv("device-1", "192.168.1.20", "chromecast"))
        assertTrue(remote.state.value.pairableTvs.isEmpty())
        assertEquals(1, deviceStore.saved.paired.size)
    }

    @Test
    fun `playing on a paired TV wakes it with the play link instead of the server command`() =
        runTest {
            deviceStore.saved =
                SavedRemoteDevices(paired = listOf(PairedTv("device-1", "10.0.0.1", "TV")))
            val remote = remote()
            remote.refresh()

            remote.launchPlay(testRemoteDevice(), "abc", 5_000, StreamChoice(1, -1))
            advanceTimeBy(BACKGROUND_WORK_MS)
            runCurrent()

            assertEquals(
                listOf("10.0.0.1" to "jellycast://play?item=abc&start=5000&audio=1&subtitle=-1"),
                link.opened,
            )
            assertTrue(api.sent.isEmpty())
            assertNull(remote.state.value.waking)
        }

    @Test
    fun `playing on an unpaired TV sends play now through the server`() = runTest {
        val remote = remote()

        remote.launchPlay(testRemoteDevice(), "abc", 5_000, null)
        advanceTimeBy(BACKGROUND_WORK_MS)
        runCurrent()

        assertEquals(
            RemoteCommand.Play("abc", 5_000, StreamChoice(null, null)) to "session-1",
            api.sent.first(),
        )
    }

    @Test
    fun `a failed wake shows a failure`() = runTest {
        link.failWake = true
        val remote = remote()

        remote.wakeAndPlay(PairedTv("device-1", "10.0.0.1", "TV"), "abc", 0, null)

        assertTrue(remote.state.value.commandFailed)
    }

    @Test
    fun `a paired TV that refuses this phone plays through the server and asks to pair again`() =
        runTest {
            deviceStore.saved =
                SavedRemoteDevices(paired = listOf(PairedTv("device-1", "10.0.0.1", "TV")))
            link.failWake = true
            link.wakeError = SSLHandshakeException("certificate unknown")
            val remote = remote()
            remote.refresh()

            remote.launchPlay(testRemoteDevice(), "abc", 5_000, null)
            advanceTimeBy(BACKGROUND_WORK_MS)
            runCurrent()

            assertEquals(
                RemoteCommand.Play("abc", 5_000, StreamChoice(null, null)) to "session-1",
                api.sent.first(),
            )
            assertTrue(remote.state.value.paired.single().needsPairing)
            assertNull(remote.state.value.wakeableTv("device-1"))
            assertFalse(remote.state.value.commandFailed)
        }

    @Test
    fun `a TV to pair again is offered while its JellyCast is closed, not woken`() = runTest {
        deviceStore.saved =
            SavedRemoteDevices(
                paired = listOf(PairedTv("device-1", "10.0.0.1", "TV", needsPairing = true)),
            )
        link.reachableHosts += "10.0.0.1"
        val remote = remote()
        remote.refresh()
        remote.checkReachability()

        assertTrue(remote.state.value.wakeableTvs.isEmpty())
        assertEquals(listOf("device-1"), remote.state.value.tvsToPairAgain.map { it.deviceId })
    }

    @Test
    fun `handing back stops the TV and turns a paired Chromecast off`() = runTest {
        deviceStore.saved =
            SavedRemoteDevices(paired = listOf(PairedTv("device-1", "10.0.0.1", "TV")))
        val remote = remote()
        remote.refresh()

        remote.launchHandBack(testRemoteSession())
        advanceTimeBy(BACKGROUND_WORK_MS)
        runCurrent()

        assertEquals(RemoteCommand.Stop to "session-1", api.sent.first())
        assertEquals(listOf("10.0.0.1"), link.turnedOff)
    }

    @Test
    fun `an empty name goes back to the device's own`() = runTest {
        api.snapshot = RemoteSnapshot(emptyList(), listOf(testRemoteDevice(name = "chromecast")))
        val remote = remote()
        remote.refresh()

        remote.rename("device-1", "  Bedroom ")
        assertEquals("Bedroom", remote.state.value.devices.single().name)
        remote.rename("device-1", " ")

        assertEquals("chromecast", remote.state.value.devices.single().name)
        assertTrue(deviceStore.saved.names.isEmpty())
    }

    @Test
    fun `forgetting a pairing removes it`() = runTest {
        deviceStore.saved =
            SavedRemoteDevices(paired = listOf(PairedTv("device-1", "10.0.0.1", "TV")))
        val remote = remote()
        remote.refresh()

        remote.forget("device-1")

        assertTrue(remote.state.value.paired.isEmpty())
        assertTrue(deviceStore.saved.paired.isEmpty())
    }
}

// Background work isn't run by advanceUntilIdle; this covers the command's report delays.
private const val BACKGROUND_WORK_MS = 10_000L
