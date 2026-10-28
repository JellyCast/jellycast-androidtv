package nl.jellycast.core.data.repository

import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.mapper.toEpisode
import nl.jellycast.core.data.mapper.toMovie
import nl.jellycast.core.data.mapper.toSeries
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

class DefaultLibrarySyncRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val images: ImageUrlBuilder,
    private val dispatchers: AppDispatchers,
) : LibrarySyncRepository {
    override suspend fun account(): LibraryAccount? {
        val session = store.session.first()
        val serverUrl = session.serverUrl ?: return null
        val userId = session.userId ?: return null
        if (session.accessToken == null) return null
        return LibraryAccount(serverUrl, userId)
    }

    override suspend fun fetchLibrary(): Result<LibraryContent> = withContext(dispatchers.io) {
        val user =
            account()?.userId?.toUUID()
                ?: return@withContext Result.failure(SessionExpiredException())
        try {
            coroutineScope {
                val movies = async { items(user, BaseItemKind.MOVIE) }
                val series = async { items(user, BaseItemKind.SERIES) }
                val episodes = async { items(user, BaseItemKind.EPISODE) }
                Result.success(
                    LibraryContent(
                        // The sync skips media streams, so nothing can count as Atmos here.
                        movies = movies.await().map { it.toMovie(images, emptySet()) },
                        series = series.await().map { it.toSeries(images) },
                        episodes =
                            episodes.await()
                                .map { it.toEpisode(images) }
                                .filter { it.seriesId.isNotEmpty() },
                    ),
                )
            }
        } catch (e: InvalidStatusException) {
            Timber.w(e, "Library sync refused")
            Result.failure(if (e.status == HTTP_UNAUTHORIZED) SessionExpiredException() else e)
        } catch (e: ApiClientException) {
            Timber.w(e, "Library sync failed")
            Result.failure(e)
        }
    }

    private suspend fun items(user: UUID, kind: BaseItemKind): List<BaseItemDto> = api.itemsApi
        .getItems(
            userId = user,
            includeItemTypes = listOf(kind),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            fields = SyncFields,
            enableImageTypes = SyncImageTypes,
            imageTypeLimit = 1,
            enableUserData = true,
            enableTotalRecordCount = false,
        ).content.items
}

class DefaultServerStatusRepository(
    private val jellyfin: Jellyfin,
    private val api: ApiClient,
    private val dispatchers: AppDispatchers,
) : ServerStatusRepository {
    // One client per server address: the shared one waits far longer than this check may.
    private var checkClient: ApiClient? = null

    override suspend fun isReachable(): Boolean = withContext(dispatchers.io) {
        // Before the session has set the address there is nothing to check; that is not offline.
        val baseUrl = api.baseUrl ?: return@withContext true
        val client = clientFor(baseUrl)
        try {
            client.systemApi.getPublicSystemInfo()
            true
        } catch (e: ApiClientException) {
            Timber.i(e, "Server check failed")
            false
        }
    }

    @Synchronized
    private fun clientFor(baseUrl: String): ApiClient = checkClient
        ?.takeIf { it.baseUrl == baseUrl }
        ?: jellyfin.createApi(baseUrl = baseUrl, httpClientOptions = ServerStatusHttpOptions)
            .also { checkClient = it }
}

private const val HTTP_UNAUTHORIZED = 401

private val ServerStatusHttpOptions =
    HttpClientOptions(connectTimeout = 5.seconds, requestTimeout = 5.seconds)

// Everything the app derives its lists from; media streams come per item when a page opens.
private val SyncFields =
    listOf(
        ItemFields.OVERVIEW,
        ItemFields.DATE_CREATED,
        ItemFields.GENRES,
        ItemFields.PROVIDER_IDS,
        ItemFields.DATE_LAST_MEDIA_ADDED,
    )

private val SyncImageTypes =
    listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.THUMB, ImageType.LOGO)
