package nl.jellycast.core.data.repository

import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.datastore.SessionStore
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

/** What the quality choice needs to know about a file: its bitrate and picture width. */
data class MediaSourceSummary(val bitrate: Long?, val width: Int?)

interface MediaSourceRepository {
    /** The first media source of the item; `null` when the server does not answer. */
    suspend fun getSourceSummary(itemId: String): MediaSourceSummary?
}

class DefaultMediaSourceRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val dispatchers: AppDispatchers,
) : MediaSourceRepository {
    override suspend fun getSourceSummary(itemId: String): MediaSourceSummary? =
        withContext(dispatchers.io) {
            try {
                val item =
                    api.itemsApi
                        .getItems(
                            userId = userId(),
                            ids = listOf(itemId.toUUID()),
                            fields = listOf(ItemFields.MEDIA_SOURCES),
                        ).content.items
                        .firstOrNull()
                val source = item?.mediaSources?.firstOrNull() ?: return@withContext null
                MediaSourceSummary(
                    bitrate = source.bitrate?.toLong(),
                    width =
                        source.mediaStreams
                            ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                            ?.width,
                )
            } catch (e: ApiClientException) {
                Timber.w(e, "Media source of %s failed", itemId)
                null
            }
        }

    private suspend fun userId(): UUID =
        requireNotNull(store.session.first().userId) { "Not logged in" }.toUUID()
}
