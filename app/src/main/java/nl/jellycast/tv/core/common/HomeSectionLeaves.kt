package nl.jellycast.tv.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import nl.jellycast.tv.core.model.HomeSection

/**
 * Counts how often each Home section was left via the section bar (not by opening a screen from
 * it), so a hidden section can prepare its next visit before it shows again.
 */
class HomeSectionLeaves {
    private val counts = MutableStateFlow<Map<HomeSection, Int>>(emptyMap())

    fun onLeft(section: HomeSection) {
        counts.update { it + (section to (it[section] ?: 0) + 1) }
    }

    /** Emits once each time [section] is left from now on. */
    fun leavesOf(section: HomeSection): Flow<Unit> =
        counts.map { it[section] ?: 0 }.distinctUntilChanged().drop(1).map { }
}
