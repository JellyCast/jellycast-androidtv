package nl.jellycast.core.model

/** A Jellyfin playlist of movies and episodes, in its own order. */
data class Playlist(val id: String, val name: String, val entries: List<PlaylistEntry>)

/** [entryId] names this place in the list; the same item can stand in a list twice. */
data class PlaylistEntry(val entryId: String, val itemId: String)

/** A person found by name, with a small photo when the server has one. */
data class PersonMatch(val id: String, val name: String, val imageUrl: String?)
