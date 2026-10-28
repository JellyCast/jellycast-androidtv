package nl.jellycast.mobile.core.downloads

import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.repository.ServerUserData
import nl.jellycast.mobile.testing.FakeDownloadRepository
import nl.jellycast.mobile.testing.networkDrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineProgressTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val repository = FakeDownloadRepository()
    private var now = 5_000L

    private fun progress() = OfflineProgress(DownloadFiles(folder.root), repository, Clock { now })

    @Test
    fun `a newer position is sent with the time it was watched`() = runTest {
        repository.userData["e1"] = ServerUserData(0, played = false, lastPlayedAtMs = 1_000)
        val progress = progress()
        progress.record("e1", positionMs = 50_000, finished = false)

        assertTrue(progress.send())

        assertEquals(ServerUserData(500_000_000, false, 5_000), repository.userData["e1"])
        assertTrue(progress.pending().isEmpty())
    }

    @Test
    fun `a position older than the server's is dropped`() = runTest {
        repository.userData["e1"] = ServerUserData(0, played = false, lastPlayedAtMs = 9_000)
        val progress = progress()
        progress.record("e1", positionMs = 50_000, finished = false)

        assertFalse(progress.send())

        assertTrue(repository.sentUserData.isEmpty())
        assertTrue(progress.pending().isEmpty())
    }

    @Test
    fun `without the server the position stays, also after a relaunch`() = runTest {
        repository.userDataFailure = networkDrop()
        progress().record("e1", positionMs = 50_000, finished = true)

        val relaunched = progress()
        assertFalse(relaunched.send())

        val pending = relaunched.pending().getValue("e1")
        assertTrue(pending.played)
        assertEquals(0L, pending.positionMs)
        assertEquals(5_000L, pending.watchedAtMs)
    }
}
