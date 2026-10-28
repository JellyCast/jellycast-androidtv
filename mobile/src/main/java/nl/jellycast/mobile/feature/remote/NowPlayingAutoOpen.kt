package nl.jellycast.mobile.feature.remote

import nl.jellycast.core.common.Clock
import nl.jellycast.mobile.core.remote.RemoteSession
import nl.jellycast.mobile.core.remote.RemoteState

/**
 * Opens the now-playing screen when the app opens while a TV plays, as if the TV bar was tapped.
 * Only the first server answer after opening decides, and only when it comes within
 * [WINDOW_MS]: a TV that starts later gets the TV bar, never a screen popping up during use.
 * Decides once per opening, so a closed screen stays closed until the next one.
 */
class NowPlayingAutoOpen(private val clock: Clock) {
    private var openedAtMs: Long? = null

    /** The app came to the foreground: cold start or back from the background. */
    fun onAppOpened() {
        openedAtMs = clock.nowMs()
    }

    /** The session to open for [state], or null. Returns one at most once per opening. */
    fun sessionToOpen(state: RemoteState, isPlayerOpen: Boolean): RemoteSession? {
        val openedAt = openedAtMs ?: return null
        val answeredAt = state.refreshedAtMs ?: return null
        if (answeredAt < openedAt) return null
        openedAtMs = null
        val tooLate = answeredAt - openedAt > WINDOW_MS
        if (tooLate || isPlayerOpen || state.shownSessionId != null) return null
        return state.current
    }

    private companion object {
        const val WINDOW_MS = 5_000L
    }
}
