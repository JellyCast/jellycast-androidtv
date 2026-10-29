package nl.jellycast.core.data.repository

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.jellycast.core.common.Clock
import nl.jellycast.core.datastore.ServiceStore
import nl.jellycast.core.model.AdPlacements
import nl.jellycast.core.network.JellyCastService
import nl.jellycast.core.network.parsePlacements

/**
 * What the service wants on each ad placement, kept between launches so Home doesn't wait for
 * it. Nothing shows when the service never answered or a campaign has ended.
 */
interface AdRepository {
    val placements: Flow<AdPlacements?>

    /** At launch and when the app comes back; asks the service at most every 15 minutes. */
    suspend fun refresh()
}

private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L

class DefaultAdRepository(
    private val store: ServiceStore,
    private val service: JellyCastService,
    private val clock: Clock,
) : AdRepository {
    private var refreshedAtMs: Long? = null

    override val placements: Flow<AdPlacements?> =
        store.placementsJson.map { json ->
            val placements = json?.let(::parsePlacements) ?: return@map null
            val now = clock.nowMs()
            placements.copy(
                home = placements.home?.takeIf { it.untilMs > now },
                splash = placements.splash?.takeIf { it.untilMs > now },
            )
        }

    override suspend fun refresh() {
        val now = clock.nowMs()
        val last = refreshedAtMs
        if (last != null && now - last < REFRESH_INTERVAL_MS) return
        refreshedAtMs = now
        val json =
            try {
                service.placements()
            } catch (_: IOException) {
                refreshedAtMs = null
                return
            }
        if (parsePlacements(json) != null) store.savePlacements(json)
    }
}
