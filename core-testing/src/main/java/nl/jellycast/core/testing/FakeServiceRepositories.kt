package nl.jellycast.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.jellycast.core.data.repository.AdRepository
import nl.jellycast.core.data.repository.UsageRepository
import nl.jellycast.core.model.AdCampaign
import nl.jellycast.core.model.AdPlacements
import nl.jellycast.core.model.UsageChoice

class FakeUsageRepository(initial: UsageChoice? = UsageChoice.Declined) : UsageRepository {
    override val choice: StateFlow<UsageChoice?> = MutableStateFlow(initial)
    var opened = 0
        private set

    override suspend fun choose(choice: UsageChoice) {
        (this.choice as MutableStateFlow).value = choice
    }

    override suspend fun appOpened() {
        opened++
    }
}

class FakeAdRepository(home: AdCampaign? = null) : AdRepository {
    override val placements = MutableStateFlow<AdPlacements?>(AdPlacements("NL", home, null))
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
    }
}

fun testCampaign(id: Int = 1, position: Int? = 3, untilMs: Long = Long.MAX_VALUE) = AdCampaign(
    id = id,
    position = position,
    imageUrl = "https://example.com/ad-$id.jpg",
    title = "Big Buck Bunny",
    advertiser = "Blender Foundation",
    url = "https://www.blender.org",
    seconds = null,
    untilMs = untilMs,
)
