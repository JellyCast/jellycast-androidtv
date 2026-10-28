package nl.jellycast.core.data.repository

import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.mapper.toJellyfinId
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.model.Playlist
import nl.jellycast.core.model.PlaylistEntry
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.UpdatePlaylistDto
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

/** The user's own lists: Jellyfin playlists of movies and episodes. */
interface PlaylistRepository {
    /** A–Z, each with its entries in order. */
    suspend fun getPlaylists(): Result<List<Playlist>>

    suspend fun getEntries(playlistId: String): Result<List<PlaylistEntry>>

    /** Returns the new list's id. A season or series in [itemIds] is added as its episodes. */
    suspend fun create(name: String, itemIds: List<String>): Result<String>

    /** At the bottom of the list. */
    suspend fun add(playlistId: String, itemIds: List<String>): Result<Unit>

    suspend fun remove(playlistId: String, entryIds: List<String>): Result<Unit>

    suspend fun move(playlistId: String, entryId: String, newIndex: Int): Result<Unit>

    suspend fun rename(playlistId: String, name: String): Result<Unit>

    /** Removes the list only; its movies and episodes stay in the library. */
    suspend fun delete(playlistId: String): Result<Unit>
}

class DefaultPlaylistRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val dispatchers: AppDispatchers,
) : PlaylistRepository {
    override suspend fun getPlaylists(): Result<List<Playlist>> = call {
        val playlists =
            api.itemsApi
                .getItems(
                    userId = userId(),
                    includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    enableTotalRecordCount = false,
                ).content.items
                .filter { it.mediaType != MediaType.AUDIO }
        coroutineScope {
            playlists
                .map { playlist ->
                    async {
                        Playlist(
                            id = playlist.id.toJellyfinId(),
                            name = playlist.name.orEmpty(),
                            entries = entries(playlist.id),
                        )
                    }
                }.awaitAll()
        }
    }

    override suspend fun getEntries(playlistId: String): Result<List<PlaylistEntry>> = call {
        entries(playlistId.toUUID())
    }

    override suspend fun create(name: String, itemIds: List<String>): Result<String> = call {
        api.playlistsApi
            .createPlaylist(
                CreatePlaylistDto(
                    name = name,
                    ids = itemIds.map { it.toUUID() },
                    userId = userId(),
                    mediaType = MediaType.VIDEO,
                    users = emptyList(),
                    isPublic = false,
                ),
            ).content.id
    }

    override suspend fun add(playlistId: String, itemIds: List<String>): Result<Unit> = call {
        api.playlistsApi.addItemToPlaylist(
            playlistId = playlistId.toUUID(),
            ids = itemIds.map { it.toUUID() },
            userId = userId(),
        )
        Unit
    }

    override suspend fun remove(playlistId: String, entryIds: List<String>): Result<Unit> = call {
        api.playlistsApi.removeItemFromPlaylist(playlistId, entryIds)
        Unit
    }

    // The server refuses Move with an API key (400) but accepts it with the user's own token,
    // which is what the app signs in with.
    override suspend fun move(playlistId: String, entryId: String, newIndex: Int): Result<Unit> =
        call {
            api.playlistsApi.moveItem(playlistId, entryId, newIndex)
            Unit
        }

    override suspend fun rename(playlistId: String, name: String): Result<Unit> = call {
        api.playlistsApi.updatePlaylist(playlistId.toUUID(), UpdatePlaylistDto(name = name))
        Unit
    }

    override suspend fun delete(playlistId: String): Result<Unit> = call {
        api.libraryApi.deleteItem(playlistId.toUUID())
        Unit
    }

    private suspend fun entries(playlistId: UUID): List<PlaylistEntry> = api.playlistsApi
        .getPlaylistItems(
            playlistId = playlistId,
            userId = userId(),
            enableImages = false,
            enableUserData = false,
        ).content.items
        .mapNotNull { it.toPlaylistEntry() }

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        withContext(dispatchers.io) {
            try {
                Result.success(block())
            } catch (e: ApiClientException) {
                Timber.w(e, "A list request failed")
                Result.failure(e)
            }
        }

    private suspend fun userId(): UUID =
        requireNotNull(store.session.first().userId) { "Not logged in" }.toUUID()
}

private fun BaseItemDto.toPlaylistEntry(): PlaylistEntry? =
    playlistItemId?.let { PlaylistEntry(entryId = it, itemId = id.toJellyfinId()) }
