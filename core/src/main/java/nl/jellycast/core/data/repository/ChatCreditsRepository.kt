package nl.jellycast.core.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.model.ChatCredit
import nl.jellycast.core.model.ChatCredits
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

/** The credits of a movie or episode for the AI chat's context. */
interface ChatCreditsRepository {
    /** Episodes lack actors now and then: [seriesId] fills them in from the series. */
    suspend fun getCredits(itemId: String, seriesId: String?): ChatCredits?
}

class DefaultChatCreditsRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val dispatchers: AppDispatchers,
) : ChatCreditsRepository {
    override suspend fun getCredits(itemId: String, seriesId: String?): ChatCredits? =
        withContext(dispatchers.io) {
            try {
                val own = people(itemId)
                val series = seriesId?.let { peopleOrEmpty(it) }.orEmpty()
                ChatCredits(
                    actors = own.of(PersonKind.ACTOR).ifEmpty { series.of(PersonKind.ACTOR) },
                    guestStars = own.of(PersonKind.GUEST_STAR),
                    directors = own.of(PersonKind.DIRECTOR),
                    writers = own.of(PersonKind.WRITER),
                )
            } catch (e: ApiClientException) {
                Timber.w(e, "Could not load credits for the chat")
                null
            }
        }

    private suspend fun people(itemId: String): List<BaseItemPerson> {
        val userId = store.session.first().userId?.toUUID()
        return api.userLibraryApi.getItem(itemId.toUUID(), userId).content.people.orEmpty()
    }

    private suspend fun peopleOrEmpty(itemId: String): List<BaseItemPerson> = try {
        people(itemId)
    } catch (e: ApiClientException) {
        Timber.w(e, "Could not load the series credits for the chat")
        emptyList()
    }
}

private fun List<BaseItemPerson>.of(kind: PersonKind): List<ChatCredit> = filter { it.type == kind }
    .mapNotNull { person -> person.name?.let { ChatCredit(it, person.role) } }
    .distinctBy { it.name }
