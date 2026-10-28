package nl.jellycast.core.data.repository

import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.mapper.audioTracks
import nl.jellycast.core.data.mapper.playMethod
import nl.jellycast.core.data.mapper.subtitleTracks
import nl.jellycast.core.data.mapper.toMediaItemSummary
import nl.jellycast.core.data.mapper.transcodeReasons
import nl.jellycast.core.data.mapper.videoInfo
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.model.MediaItemSummary
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.msToTicks
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

data class PlaybackRequest(
    val itemId: String,
    val deviceProfile: DeviceProfile,
    val maxStreamingBitrate: Int,
    val audioStreamIndex: Int? = null,
    // Left null, the server picks a subtitle and burns in image subtitles when transcoding,
    // which forces a full video transcode. With direct play the player picks subtitles itself.
    val subtitleStreamIndex: Int? = NO_SUBTITLE,
    val startPositionMs: Long? = null,
    // The server only applies track choices to the source with this id.
    val mediaSourceId: String? = null,
    val allowDirectPlay: Boolean = true,
    val allowVideoCopy: Boolean = true,
)

const val NO_SUBTITLE = -1

interface PlaybackRepository {
    suspend fun getPlaybackPlan(request: PlaybackRequest): PlaybackPlan

    /** In the given order; unknown ids are dropped. */
    suspend fun getItems(ids: List<String>): List<MediaItemSummary>

    suspend fun getMovies(limit: Int): List<MediaItemSummary>
}

class DefaultPlaybackRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val dispatchers: AppDispatchers,
) : PlaybackRepository {
    override suspend fun getPlaybackPlan(request: PlaybackRequest): PlaybackPlan =
        withContext(dispatchers.io) {
            val itemId = request.itemId.toUUID()
            val response =
                api.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            userId = userId(),
                            maxStreamingBitrate = request.maxStreamingBitrate,
                            startTimeTicks = request.startPositionMs?.msToTicks(),
                            audioStreamIndex = request.audioStreamIndex,
                            subtitleStreamIndex = request.subtitleStreamIndex,
                            deviceProfile = request.deviceProfile,
                            // With a single version the source id equals the item id.
                            mediaSourceId =
                                request.mediaSourceId ?: itemId.toString().replace("-", ""),
                            enableDirectPlay = request.allowDirectPlay,
                            enableDirectStream = true,
                            enableTranscoding = true,
                            allowVideoStreamCopy = request.allowVideoCopy,
                            allowAudioStreamCopy = true,
                            autoOpenLiveStream = true,
                        ),
                    ).content
            val source =
                response.mediaSources.firstOrNull()
                    ?: error("No playable source for ${request.itemId}")
            val mediaSourceId = source.id ?: request.itemId
            val method = source.playMethod()
            source.transcodingUrl?.let {
                Timber.d("Transcode parameters: %s", it.withoutCredentials())
            }
            val url =
                when (method) {
                    PlayMethod.DirectPlay ->
                        api.videosApi.getVideoStreamUrl(
                            itemId = itemId,
                            container = source.container,
                            static = true,
                            mediaSourceId = mediaSourceId,
                            playSessionId = response.playSessionId,
                            tag = source.eTag,
                        )

                    PlayMethod.DirectStream, PlayMethod.Transcode ->
                        api.baseUrl.orEmpty().trimEnd('/') +
                            requireNotNull(source.transcodingUrl) {
                                "Server returned no transcoding URL"
                            }.toSingleVariantUrl()
                }

            PlaybackPlan(
                itemId = request.itemId,
                title = source.name.orEmpty(),
                mediaSourceId = mediaSourceId,
                playSessionId = response.playSessionId,
                streamUrl = url,
                playMethod = method,
                transcodeReasons = transcodeReasons(source.transcodingUrl),
                container = source.container,
                video = source.videoInfo(),
                audioTracks = source.audioTracks(),
                subtitleTracks = source.subtitleTracks(),
                audioStreamIndex = request.audioStreamIndex ?: source.defaultAudioStreamIndex,
                subtitleStreamIndex = request.subtitleStreamIndex?.takeIf { it >= 0 },
                startPositionMs = request.startPositionMs ?: 0L,
                preferredLanguages = store.session.first().preferredLanguages,
            )
        }

    override suspend fun getItems(ids: List<String>): List<MediaItemSummary> =
        withContext(dispatchers.io) {
            if (ids.isEmpty()) return@withContext emptyList()
            val uuids = ids.map { it.toUUID() }
            val items =
                api.itemsApi
                    .getItems(userId = userId(), ids = uuids, enableUserData = true)
                    .content.items
                    .associateBy { it.id }
            uuids.mapNotNull { items[it]?.toMediaItemSummary() }
        }

    override suspend fun getMovies(limit: Int): List<MediaItemSummary> =
        withContext(dispatchers.io) {
            api.itemsApi
                .getItems(
                    userId = userId(),
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    limit = limit,
                    enableUserData = true,
                ).content.items
                .map { it.toMediaItemSummary() }
        }

    private suspend fun userId(): UUID =
        requireNotNull(store.session.first().userId) { "Not logged in" }.toUUID()
}

private fun String.withoutCredentials(): String = substringAfter('?')
    .split('&')
    .filterNot {
        it.startsWith("api_key=", ignoreCase = true) ||
            it.startsWith("ApiKey=", ignoreCase = true)
    }
    .joinToString("&")

// Jellyfin's master playlist also lists transcoded SDR variants, and ExoPlayer picked one, making
// the server transcode 4K HDR when only audio needed it. `main.m3u8` is the stream-copy variant.
private fun String.toSingleVariantUrl(): String = replace("/master.m3u8", "/main.m3u8")
