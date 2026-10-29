package nl.jellycast.core.data.repository

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.jellycast.core.common.Clock
import nl.jellycast.core.datastore.ServiceStore
import nl.jellycast.core.model.UsageChoice
import nl.jellycast.core.network.JellyCastService

/** Whether this device may be counted, and the ping that allows. */
interface UsageRepository {
    /** `null` while the user hasn't answered the current version of the question. */
    val choice: Flow<UsageChoice?>

    suspend fun choose(choice: UsageChoice)

    /** At launch and when the app comes back; sends at most one ping an hour. */
    suspend fun appOpened()
}

/** Raise when what is sent or why changes, so everyone is asked again. Same as iOS. */
const val USAGE_CONSENT_VERSION = 2

private const val PING_INTERVAL_MS = 60 * 60 * 1000L

class DefaultUsageRepository(
    private val store: ServiceStore,
    private val service: JellyCastService,
    private val clock: Clock,
) : UsageRepository {
    private var lastPingAtMs: Long? = null

    override val choice: Flow<UsageChoice?> =
        store.consent.map { consent ->
            if (consent.version != USAGE_CONSENT_VERSION) return@map null
            UsageChoice.entries.firstOrNull { it.name == consent.choice }
        }

    override suspend fun choose(choice: UsageChoice) {
        val installId =
            when (choice) {
                UsageChoice.Allowed -> store.installId() ?: UUID.randomUUID().toString()
                UsageChoice.Declined -> null
            }
        store.saveConsent(choice.name, USAGE_CONSENT_VERSION, clock.nowMs(), installId)
        if (choice == UsageChoice.Allowed) appOpened() else lastPingAtMs = null
    }

    override suspend fun appOpened() {
        val now = clock.nowMs()
        val last = lastPingAtMs
        if (last != null && now - last < PING_INTERVAL_MS) return
        val installId = store.installId() ?: return
        lastPingAtMs = now
        try {
            service.ping(installId)
        } catch (_: IOException) {
            lastPingAtMs = null
        }
    }
}
