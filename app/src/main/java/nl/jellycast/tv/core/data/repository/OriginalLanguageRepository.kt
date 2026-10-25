package nl.jellycast.tv.core.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.jellycast.tv.core.common.AppDispatchers
import nl.jellycast.tv.core.datastore.SessionStore
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

/**
 * Jellyfin keeps no original language, and dubbed releases mark their dub as the default track.
 * Wikidata knows the original language of a film or series by its IMDb id, without an API key.
 */
fun interface OriginalLanguageRepository {
    /** ISO 639-1 codes ("en"); empty when unknown or unreachable. */
    suspend fun originalLanguages(itemId: String): List<String>
}

class WikidataOriginalLanguageRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val dispatchers: AppDispatchers,
) : OriginalLanguageRepository {
    // Per IMDb id; episodes share their series' entry.
    private val cache = ConcurrentHashMap<String, List<String>>()

    override suspend fun originalLanguages(itemId: String): List<String> =
        withContext(dispatchers.io) {
            try {
                val imdbId = imdbIdOf(itemId) ?: return@withContext emptyList()
                cache[imdbId] ?: queryWikidata(imdbId).also { cache[imdbId] = it }
            } catch (e: ApiClientException) {
                Timber.d(e, "No original language for %s", itemId)
                emptyList()
            } catch (e: IOException) {
                Timber.d(e, "Wikidata unreachable for %s", itemId)
                emptyList()
            } catch (e: SerializationException) {
                Timber.d(e, "Unexpected Wikidata answer for %s", itemId)
                emptyList()
            } catch (e: IllegalArgumentException) {
                // A JSON element of another type than expected.
                Timber.d(e, "Unexpected Wikidata answer for %s", itemId)
                emptyList()
            }
        }

    // Episode IMDb ids rarely carry a language on Wikidata.
    private suspend fun imdbIdOf(itemId: String): String? {
        val item = item(itemId.toUUID()) ?: return null
        val title = item.seriesId?.let { item(it) } ?: item
        return title.providerIds?.get("Imdb")?.takeIf { it.startsWith("tt") }
    }

    private suspend fun item(id: UUID): BaseItemDto? = api.itemsApi
        .getItems(
            userId = userId(),
            ids = listOf(id),
            fields = listOf(ItemFields.PROVIDER_IDS),
            enableImages = false,
        ).content.items
        .firstOrNull()

    private fun queryWikidata(imdbId: String): List<String> {
        val query =
            "SELECT ?code WHERE { ?work wdt:P345 \"$imdbId\"; wdt:P364 ?language. " +
                "?language wdt:P218 ?code }"
        val url = URL("$WIKIDATA_SPARQL?format=json&query=${URLEncoder.encode(query, "UTF-8")}")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = WIKIDATA_TIMEOUT_MS
            connection.readTimeout = WIKIDATA_TIMEOUT_MS
            // Wikidata refuses requests without a descriptive User-Agent.
            connection.setRequestProperty("User-Agent", "JellyCast/1.0 (personal Jellyfin client)")
            connection.setRequestProperty("Accept", "application/sparql-results+json")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val languages = parseLanguageCodes(body)
            Timber.i("Original language of %s: %s", imdbId, languages)
            return languages
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun userId(): UUID =
        requireNotNull(store.session.first().userId) { "Not logged in" }.toUUID()
}

internal fun parseLanguageCodes(body: String): List<String> =
    Json.parseToJsonElement(body).jsonObject["results"]
        ?.jsonObject
        ?.get("bindings")
        ?.jsonArray
        .orEmpty()
        .mapNotNull { it.jsonObject["code"]?.jsonObject?.get("value")?.jsonPrimitive?.content }
        .distinct()

private const val WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"

// Playback waits for this; without an answer the fallback languages decide.
private const val WIKIDATA_TIMEOUT_MS = 2_500
