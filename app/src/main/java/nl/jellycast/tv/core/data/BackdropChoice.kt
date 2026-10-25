package nl.jellycast.tv.core.data

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Remembers which backdrop each movie or series shows, so every screen shows the same one. It
 * only moves on with [advance], when a section is left: the hidden sections update right away, so
 * nothing changes on screen when one comes back.
 */
class BackdropChoice(private val random: Random = Random.Default) {
    private class Pick(val index: Int, val count: Int)

    private val picks = ConcurrentHashMap<String, Pick>()
    private val _changes = MutableStateFlow(0)

    /** Increases after every [advance]; screens then apply [index] again. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun index(ownerId: String, count: Int): Int {
        if (count <= 1) return 0
        val pick =
            picks.compute(ownerId) { _, previous ->
                previous?.takeIf { it.count == count } ?: Pick(random.nextInt(count), count)
            }
        return requireNotNull(pick) { "compute never stores null here" }.index
    }

    /** The next backdrop for each owner, in turn, so a few visits show all of them. */
    fun advance(ownerIds: Collection<String>) {
        var changed = false
        for (ownerId in ownerIds) {
            picks.computeIfPresent(ownerId) { _, pick ->
                changed = changed || pick.count > 1
                Pick((pick.index + 1) % pick.count, pick.count)
            }
        }
        if (changed) _changes.update { it + 1 }
    }
}
