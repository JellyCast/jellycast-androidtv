package nl.jellycast.core.network

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.jellycast.core.model.AdCampaign
import nl.jellycast.core.model.AdPlacements
import nl.jellycast.core.model.ServiceClientInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The JellyCast service (`../jellycast-api`): usage pings and ad placements. */
interface JellyCastService {
    suspend fun ping(installId: String)

    /** The placements as the service sends them; read them with [parsePlacements]. */
    suspend fun placements(): String
}

class HttpJellyCastService(
    private val client: ServiceClientInfo,
    private val io: CoroutineDispatcher,
    private val http: OkHttpClient =
        OkHttpClient.Builder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
) : JellyCastService {
    override suspend fun ping(installId: String) {
        val device = client.device
        val body =
            buildJsonObject {
                put("id", installId)
                put("platform", client.platform)
                put("brand", device.brand)
                put("model", device.model)
                put("os", device.os)
                put("osVersion", device.osVersion)
                put("appVersion", device.appVersion)
            }.toString().toRequestBody(JSON)
        firstAnswer("v1/ping", body)
    }

    override suspend fun placements(): String =
        firstAnswer("v1/placements?platform=${client.platform}", body = null)

    private suspend fun firstAnswer(path: String, body: RequestBody?): String = withContext(io) {
        var lastError = IOException("No service address")
        for (address in client.addresses) {
            val request =
                Request.Builder()
                    .url("$address/$path")
                    .header("Accept", "application/json")
                    .apply { if (body != null) post(body) }
                    .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext response.body?.string().orEmpty()
                    lastError = IOException("HTTP ${response.code} from $address")
                }
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError
    }

    private companion object {
        const val TIMEOUT_SECONDS = 4L
        val JSON = "application/json".toMediaType()
    }
}

private val serviceJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class PlacementsDto(
    val country: String? = null,
    val home: CampaignDto? = null,
    val splash: CampaignDto? = null,
)

@Serializable
internal data class CampaignDto(
    val id: Int,
    val position: Int? = null,
    val image: String,
    val title: String? = null,
    val advertiser: String,
    val url: String? = null,
    val seconds: Int? = null,
    val until: String,
)

/** `null` for text that isn't placements, such as an error page. */
fun parsePlacements(text: String): AdPlacements? = try {
    serviceJson.decodeFromString<PlacementsDto>(text).toPlacements()
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

internal fun PlacementsDto.toPlacements(): AdPlacements =
    AdPlacements(country = country, home = home?.toCampaign(), splash = splash?.toCampaign())

private fun CampaignDto.toCampaign(): AdCampaign? {
    val untilMs =
        try {
            Instant.parse(until).toEpochMilli()
        } catch (_: java.time.format.DateTimeParseException) {
            return null
        }
    return AdCampaign(id, position, image, title, advertiser, url, seconds, untilMs)
}
