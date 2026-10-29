package nl.jellycast.core.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The first of [addresses], in the user's order, for which [answers] is true; `null` when none
 * does. All are asked at once. A server on the LAN answers in milliseconds, so once a less
 * preferred address has answered, the ones above it get only [graceMs] more.
 */
suspend fun pickAddress(
    addresses: List<String>,
    graceMs: Long,
    answers: suspend (String) -> Boolean,
): String? {
    if (addresses.isEmpty()) return null
    return coroutineScope {
        val answered = arrayOfNulls<Boolean>(addresses.size)
        val events = Channel<Int>(Channel.UNLIMITED)
        addresses.forEachIndexed { index, address ->
            launch {
                answered[index] = answers(address)
                events.send(index)
            }
        }
        var isGraceOver = false
        var grace: Job? = null
        var picked: String? = null
        while (true) {
            if (events.receive() == GRACE_OVER) isGraceOver = true
            val first = firstUsable(answered, isGraceOver)
            if (first != null) {
                picked = addresses[first]
                break
            }
            if (answered.all { it == false }) break
            if (grace == null && answered.any { it == true }) {
                grace = launch {
                    delay(graceMs)
                    events.send(GRACE_OVER)
                }
            }
        }
        coroutineContext.cancelChildren()
        picked
    }
}

/** The most preferred address that answered, once every one above it failed or had its grace. */
private fun firstUsable(answered: Array<Boolean?>, isGraceOver: Boolean): Int? {
    answered.forEachIndexed { index, answer ->
        when (answer) {
            true -> return index
            false -> Unit
            null -> if (!isGraceOver) return null
        }
    }
    return null
}

private const val GRACE_OVER = -1
