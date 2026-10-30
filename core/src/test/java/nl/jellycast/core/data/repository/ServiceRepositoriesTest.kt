package nl.jellycast.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.datastore.ServiceStore
import nl.jellycast.core.model.UsageChoice
import nl.jellycast.core.testing.FakeJellyCastService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServiceRepositoriesTest {
    @get:Rule val folder = TemporaryFolder()

    private var nowMs = 1_800_000_000_000L
    private val clock = Clock { nowMs }
    private val service = FakeJellyCastService()

    private fun TestScope.store() = ServiceStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "service.preferences_pb")
        },
    )

    @Test
    fun `nobody has answered until the current version of the question is answered`() = runTest {
        val usage = DefaultUsageRepository(store(), service, clock)

        assertNull(usage.choice.first())
        usage.choose(UsageChoice.Declined)
        assertEquals(UsageChoice.Declined, usage.choice.first())
    }

    @Test
    fun `allowing pings with one install id, at most once an hour`() = runTest {
        val usage = DefaultUsageRepository(store(), service, clock)

        usage.choose(UsageChoice.Allowed)
        nowMs += 30 * 60_000
        usage.appOpened()
        nowMs += 31 * 60_000
        usage.appOpened()

        assertEquals(2, service.pings.size)
        assertEquals(1, service.pings.toSet().size)
    }

    @Test
    fun `declining deletes the install id and sends nothing`() = runTest {
        val store = store()
        val usage = DefaultUsageRepository(store, service, clock)
        usage.choose(UsageChoice.Allowed)

        usage.choose(UsageChoice.Declined)
        nowMs += 2 * 60 * 60_000
        usage.appOpened()

        assertNull(store.installId())
        assertEquals(1, service.pings.size)
    }

    @Test
    fun `a failed ping is tried again on the next opening`() = runTest {
        val usage = DefaultUsageRepository(store(), service, clock)
        service.failNext = true

        usage.choose(UsageChoice.Allowed)
        usage.appOpened()

        assertEquals(1, service.pings.size)
    }

    @Test
    fun `a running home campaign is kept between launches and gone once it ends`() = runTest {
        val store = store()
        service.placementsJson =
            """{"version":1,"country":"NL","home":{"id":7,"position":3,""" +
            """"image":"https://x/a.jpg","advertiser":"Blender",""" +
            """"until":"2027-06-01T00:00:00Z","extra":1},"splash":null}"""
        DefaultAdRepository(store, service, clock).refresh()

        val nextLaunch = DefaultAdRepository(store, service, clock)
        assertEquals(7, nextLaunch.placements.first()?.home?.id)
        nowMs = java.time.Instant.parse("2027-06-01T00:00:00Z").toEpochMilli()
        nextLaunch.refresh()
        assertNull(nextLaunch.placements.first()?.home)
    }

    @Test
    fun `a campaign stops showing after a day without an answer from the service`() = runTest {
        val store = store()
        service.placementsJson =
            """{"version":1,"country":"NL","home":{"id":7,"position":3,""" +
            """"image":"https://x/a.jpg","advertiser":"Blender",""" +
            """"until":"2027-06-01T00:00:00Z"},"splash":null}"""
        DefaultAdRepository(store, service, clock).refresh()

        nowMs += 23 * 60 * 60_000
        assertEquals(7, DefaultAdRepository(store, service, clock).placements.first()?.home?.id)
        nowMs += 60 * 60_000
        val offline = DefaultAdRepository(store, service, clock)
        service.failNext = true
        offline.refresh()
        assertNull(offline.placements.first()?.home)
    }

    @Test
    fun `placements are asked at most every 15 minutes and survive a failure`() = runTest {
        val ads = DefaultAdRepository(store(), service, clock)
        ads.refresh()
        nowMs += 10 * 60_000
        ads.refresh()
        assertEquals(1, service.placementRequests)

        nowMs += 6 * 60_000
        service.failNext = true
        ads.refresh()
        assertNotNull(ads.placements.first())
    }
}
