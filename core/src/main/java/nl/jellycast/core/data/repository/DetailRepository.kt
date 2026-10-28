package nl.jellycast.core.data.repository

import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.mapper.toItemDetail
import nl.jellycast.core.data.mapper.toJellyfinId
import nl.jellycast.core.data.mapper.toPersonDetails
import nl.jellycast.core.data.mapper.toSimilarMovie
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.model.ItemDetail
import nl.jellycast.core.model.PersonDetails
import nl.jellycast.core.model.SimilarMovie
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

/** What the detail pages load beyond the library: credits, people, similar titles, the file. */
interface DetailRepository {
    /** Credits, studios and the media streams of one movie, series or episode. */
    suspend fun getItemDetail(itemId: String): Result<ItemDetail>

    /** Details of several people in one request, keyed by person id. */
    suspend fun getPersonDetails(personIds: List<String>): Result<Map<String, PersonDetails>>

    suspend fun getSimilarMovies(movieId: String): Result<List<SimilarMovie>>

    /** Ids of the movies and series a person appears in. */
    suspend fun getItemIdsWithPerson(personId: String): Result<List<String>>
}

class DefaultDetailRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val images: ImageUrlBuilder,
    private val dispatchers: AppDispatchers,
) : DetailRepository {
    // The full item carries People, Studios and MediaSources without asking for fields.
    override suspend fun getItemDetail(itemId: String): Result<ItemDetail> = load {
        api.userLibraryApi
            .getItem(itemId = itemId.toUUID(), userId = userId())
            .content
            .toItemDetail(images)
    }

    override suspend fun getPersonDetails(
        personIds: List<String>,
    ): Result<Map<String, PersonDetails>> {
        if (personIds.isEmpty()) return Result.success(emptyMap())
        return load {
            api.itemsApi
                .getItems(
                    userId = userId(),
                    ids = personIds.distinct().map { it.toUUID() },
                    fields = PersonFields,
                    enableImages = false,
                    enableUserData = false,
                    enableTotalRecordCount = false,
                ).content.items
                .associate { it.id.toJellyfinId() to it.toPersonDetails() }
        }
    }

    override suspend fun getSimilarMovies(movieId: String): Result<List<SimilarMovie>> = load {
        api.libraryApi
            .getSimilarItems(
                itemId = movieId.toUUID(),
                userId = userId(),
                limit = SIMILAR_LIMIT,
                fields = listOf(ItemFields.PEOPLE, ItemFields.STUDIOS),
            ).content.items
            .map { it.toSimilarMovie() }
    }

    override suspend fun getItemIdsWithPerson(personId: String): Result<List<String>> = load {
        api.itemsApi
            .getItems(
                userId = userId(),
                recursive = true,
                personIds = listOf(personId.toUUID()),
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                enableImages = false,
                enableUserData = false,
                enableTotalRecordCount = false,
            ).content.items
            .map { it.id.toJellyfinId() }
    }

    private suspend fun <T> load(block: suspend () -> T): Result<T> = withContext(dispatchers.io) {
        try {
            Result.success(block())
        } catch (e: ApiClientException) {
            Timber.w(e, "Loading details failed")
            Result.failure(e)
        }
    }

    private suspend fun userId(): UUID =
        requireNotNull(store.session.first().userId) { "Not logged in" }.toUUID()
}

private val PersonFields =
    listOf(
        ItemFields.OVERVIEW,
        ItemFields.PRODUCTION_LOCATIONS,
        ItemFields.PROVIDER_IDS,
    )

private const val SIMILAR_LIMIT = 20
