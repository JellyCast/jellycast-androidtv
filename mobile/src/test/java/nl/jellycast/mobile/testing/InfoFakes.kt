package nl.jellycast.mobile.testing

import kotlinx.coroutines.flow.MutableStateFlow
import nl.jellycast.core.data.repository.DetailRepository
import nl.jellycast.core.model.ItemDetail
import nl.jellycast.core.model.PersonDetails
import nl.jellycast.core.model.SimilarMovie
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.settings.RatingSource

/** Details per item id; [failing] ids fail. Requests are recorded. */
class FakeDetailRepository(
    var details: Map<String, ItemDetail> = emptyMap(),
    var people: Map<String, PersonDetails> = emptyMap(),
    var similar: Map<String, List<SimilarMovie>> = emptyMap(),
    var personItems: Map<String, List<String>> = emptyMap(),
) : DetailRepository {
    val failing = mutableSetOf<String>()
    val detailRequests = mutableListOf<String>()
    val personRequests = mutableListOf<List<String>>()

    override suspend fun getItemDetail(itemId: String): Result<ItemDetail> {
        detailRequests += itemId
        val detail = details[itemId]
        return if (itemId in failing || detail == null) failure() else Result.success(detail)
    }

    override suspend fun getPersonDetails(
        personIds: List<String>,
    ): Result<Map<String, PersonDetails>> {
        personRequests += personIds
        return Result.success(people.filterKeys { it in personIds })
    }

    override suspend fun getSimilarMovies(movieId: String): Result<List<SimilarMovie>> =
        Result.success(similar[movieId].orEmpty())

    override suspend fun getItemIdsWithPerson(personId: String): Result<List<String>> =
        if (personId in failing) failure() else Result.success(personItems[personId].orEmpty())

    private fun <T> failure(): Result<T> = Result.failure(IllegalStateException("server gone"))
}

class FakeAppPreferences(source: RatingSource = RatingSource.Imdb) : AppPreferences {
    override val ratingSource = MutableStateFlow(source)
    override val quality = MutableStateFlow<Map<NetworkKind, QualityPreference>>(emptyMap())

    override suspend fun setRatingSource(source: RatingSource) {
        ratingSource.value = source
    }

    override suspend fun setQuality(network: NetworkKind, preference: QualityPreference) {
        quality.value = quality.value + (network to preference)
    }
}
