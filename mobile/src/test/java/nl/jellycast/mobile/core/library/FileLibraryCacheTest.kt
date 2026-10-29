package nl.jellycast.mobile.core.library

import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.AgeRating
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.DynamicRange
import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Resolution
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.testing.TestAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileLibraryCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val snapshot =
        LibrarySnapshot(
            movies =
                listOf(
                    testMovie("m1", genres = listOf("Drama"), lastPlayedAt = 4).copy(
                        ageRating = AgeRating("12"),
                        artwork = Artwork(poster = "p", backdrops = listOf("b1", "b2")),
                        tech = MediaTech(Resolution.UHD, DynamicRange.DolbyVision, hasAtmos = true),
                        criticRating = 91f,
                        externalIds = ExternalIds("tt1", "2", "c"),
                    ),
                ),
            series = listOf(testSeries("s1", lastEpisodeAddedAt = 8)),
            episodes = listOf(
                testEpisode("e1", seriesId = "s1", seasonId = "season1", addedAt = 3),
            ),
            syncedAtMs = 42,
        )

    @Test
    fun `a saved library reads back the same`() = runTest {
        val cache =
            FileLibraryCache(
                File(folder.root, "library.json"),
                StandardTestDispatcher(testScheduler),
            )

        cache.save(TestAccount, snapshot)

        assertEquals(snapshot, cache.load(TestAccount))
    }

    @Test
    fun `another account or a broken file reads as nothing`() = runTest {
        val file = File(folder.root, "library.json")
        val cache = FileLibraryCache(file, StandardTestDispatcher(testScheduler))
        cache.save(TestAccount, snapshot)

        assertNull(cache.load(TestAccount.copy(userId = "u2")))
        assertNull(cache.load(TestAccount.copy(serverId = "other-server")))

        file.writeText("{ not json")
        assertNull(cache.load(TestAccount))
    }

    @Test
    fun `delete removes the file`() = runTest {
        val file = File(folder.root, "library.json")
        val cache = FileLibraryCache(file, StandardTestDispatcher(testScheduler))
        cache.save(TestAccount, snapshot)

        cache.delete()

        assertFalse(file.exists())
        assertNull(cache.load(TestAccount))
    }
}
