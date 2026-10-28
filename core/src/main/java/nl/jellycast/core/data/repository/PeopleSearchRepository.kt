package nl.jellycast.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.mapper.toJellyfinId
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.model.PersonMatch
import nl.jellycast.core.network.ImageKind
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

/** Actors, directors and other people on the server, found by name. */
fun interface PeopleSearchRepository {
    suspend fun search(term: String): Result<List<PersonMatch>>
}

class DefaultPeopleSearchRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val images: ImageUrlBuilder,
    private val dispatchers: AppDispatchers,
) : PeopleSearchRepository {
    override suspend fun search(term: String): Result<List<PersonMatch>> =
        withContext(dispatchers.io) {
            try {
                val userId =
                    requireNotNull(store.session.first().userId) { "Not logged in" }.toUUID()
                val people =
                    api.personsApi
                        .getPersons(
                            userId = userId,
                            searchTerm = term,
                            limit = SEARCH_LIMIT,
                            enableImageTypes = listOf(ImageType.PRIMARY),
                        ).content.items
                Result.success(
                    people.map { person ->
                        val id = person.id.toJellyfinId()
                        PersonMatch(
                            id = id,
                            name = person.name.orEmpty(),
                            imageUrl =
                                person.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                                    images.build(id, ImageKind.Primary, tag, PHOTO_WIDTH_PX)
                                },
                        )
                    },
                )
            } catch (e: ApiClientException) {
                Timber.w(e, "Searching people failed")
                Result.failure(e)
            }
        }
}

private const val SEARCH_LIMIT = 10

// A 60 dp circle at up to 3x density.
private const val PHOTO_WIDTH_PX = 180
