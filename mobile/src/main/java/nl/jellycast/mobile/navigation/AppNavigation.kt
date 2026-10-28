package nl.jellycast.mobile.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.StreamChoice

enum class AppTab { Home, Library, Downloads }

/** What to play. [fromStart] ignores the resume position. */
data class PlayRequest(
    val item: PlayableItem,
    val fromStart: Boolean = false,
    /** The tracks a TV played, for a play that continues here. */
    val streams: StreamChoice? = null,
    /** The list the item starts, so the player goes on with the next item. */
    val queue: PlayQueue? = null,
    /** Start here instead of the item's own resume position (a hand-over from a TV). */
    val startPositionMs: Long? = null,
)

/** A Jellyfin playlist being played item after item. */
data class PlayQueue(
    val listId: String,
    val listName: String,
    val items: List<PlayableItem>,
    val position: Int,
) {
    val current: PlayableItem? get() = items.getOrNull(position)
    val next: PlayableItem? get() = items.getOrNull(position + 1)
}

/** What "Add to list…" adds: one item, or the episodes of a season. */
data class AddToListRequest(val title: String, val itemIds: List<String>)

/** How the Library opens when another screen sends the user there. */
enum class LibraryOpenRequest { Newest, }

/**
 * The tab, each tab's back stack, and the app-wide requests (play, add to list, settings). The
 * shell owns one instance and provides it through [LocalAppNavigation]; screens only call it.
 */
@Stable
class AppNavigation {
    var tab by mutableStateOf(AppTab.Home)

    val stacks: Map<AppTab, SnapshotStateList<Route>> =
        AppTab.entries.associateWith { mutableStateListOf<Route>(TabRoot) }

    /** Set by any screen; the player host takes it and clears it. */
    var playRequest by mutableStateOf<PlayRequest?>(null)

    var addToListRequest by mutableStateOf<AddToListRequest?>(null)

    var showsSettings by mutableStateOf(false)

    var libraryOpenRequest by mutableStateOf<LibraryOpenRequest?>(null)

    val currentStack: SnapshotStateList<Route> get() = stacks.getValue(tab)

    fun open(route: Route) {
        currentStack.add(route)
    }

    fun goBack(): Boolean {
        val stack = currentStack
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun select(tab: AppTab) {
        // Tapping the open tab again returns to its root, as on iOS and in Material apps.
        if (this.tab == tab) {
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        this.tab = tab
    }

    fun play(request: PlayRequest) {
        playRequest = request
    }

    fun play(item: PlayableItem, fromStart: Boolean = false) {
        play(PlayRequest(item, fromStart))
    }

    fun addToList(title: String, itemIds: List<String>) {
        addToListRequest = AddToListRequest(title, itemIds)
    }

    fun showNewInLibrary() {
        libraryOpenRequest = LibraryOpenRequest.Newest
        select(AppTab.Library)
    }
}

val LocalAppNavigation =
    staticCompositionLocalOf<AppNavigation> { error("No AppNavigation provided") }
