package nl.jellycast.mobile.feature.home

import nl.jellycast.core.model.AdPlacements
import nl.jellycast.core.testing.testCampaign
import nl.jellycast.core.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAdTest {
    private val tiles = (1..5).map { HomeTile.NotStarted(testSeries("s$it"), 1, 1) }

    private fun placementsAt(position: Int?) =
        AdPlacements("NL", testCampaign(position = position), null)

    @Test
    fun `the ad goes on its place and pushes the rest one on`() {
        val withAd = tiles.withAd(placementsAt(3))

        assertEquals(6, withAd.size)
        assertEquals("ad-1", withAd[3].contentId)
        assertEquals(tiles.drop(3), withAd.drop(4))
    }

    @Test
    fun `the ad never takes the featured first tile and a short list gets it last`() {
        assertEquals("ad-1", tiles.withAd(placementsAt(0))[1].contentId)
        assertEquals("ad-1", tiles.withAd(placementsAt(40)).last().contentId)
    }

    @Test
    fun `without a campaign or without tiles nothing changes`() {
        assertEquals(tiles, tiles.withAd(AdPlacements("NL", null, null)))
        assertEquals(tiles, tiles.withAd(null))
        assertEquals(emptyList<HomeTile>(), emptyList<HomeTile>().withAd(placementsAt(3)))
    }
}
