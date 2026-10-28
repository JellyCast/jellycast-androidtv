package nl.jellycast.mobile.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import nl.jellycast.core.data.repository.ChatCreditsRepository
import nl.jellycast.core.model.ChatCredits
import nl.jellycast.mobile.core.chat.ChatCredentials
import nl.jellycast.mobile.core.chat.ChatHttp
import nl.jellycast.mobile.core.chat.ChatProvider
import nl.jellycast.mobile.core.chat.ChatRequest
import nl.jellycast.mobile.core.chat.ChatSettings
import nl.jellycast.mobile.core.chat.ChatSettingsState
import nl.jellycast.mobile.core.chat.ModelMemory
import nl.jellycast.mobile.core.chat.SecretCipher
import nl.jellycast.mobile.core.chat.maskKey
import nl.jellycast.mobile.core.chat.parseJson
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.feature.signin.DiscoveredServer
import nl.jellycast.mobile.feature.signin.ServerDiscovery

/** Chat settings in memory; a key per provider. */
class FakeChatSettings(provider: ChatProvider = ChatProvider.Claude, key: String? = null) :
    ChatSettings,
    ModelMemory {
    private val keys = mutableMapOf<ChatProvider, String>()
    override val state = MutableStateFlow(ChatSettingsState(provider = provider))
    var storedModel: String? = null

    init {
        if (key != null) keys[provider] = key
        publish()
    }

    override suspend fun credentials(): ChatCredentials? {
        val provider = state.value.provider
        return keys[provider]?.let { ChatCredentials(provider, it) }
    }

    override suspend fun setProvider(provider: ChatProvider) {
        state.update { it.copy(provider = provider) }
        publish()
    }

    override suspend fun setAvoidsSpoilers(avoids: Boolean) {
        state.update { it.copy(avoidsSpoilers = avoids) }
    }

    override suspend fun setSearchesWeb(searches: Boolean) {
        state.update { it.copy(searchesWeb = searches) }
    }

    override suspend fun setKey(key: String?) {
        val provider = state.value.provider
        if (key.isNullOrBlank()) keys.remove(provider) else keys[provider] = key.trim()
        publish()
    }

    override suspend fun storedModel(): String? = storedModel

    override suspend fun storeModel(model: String) {
        storedModel = model
    }

    private fun publish() {
        state.update { it.copy(maskedKey = keys[it.provider]?.let(::maskKey)) }
    }
}

/**
 * Answers each request with the next queued response: a list of server-sent events for a stream,
 * one JSON body for a load, or an error. Requests are recorded.
 */
class FakeChatHttp : ChatHttp {
    val requests = mutableListOf<ChatRequest>()
    private val responses = ArrayDeque<Result<List<JsonElement>>>()

    fun enqueueEvents(vararg events: String) {
        responses +=
            Result.success(events.map { requireNotNull(parseJson(it)) { "Bad JSON: $it" } })
    }

    fun enqueueFailure(error: Throwable) {
        responses += Result.failure(error)
    }

    override suspend fun load(request: ChatRequest): JsonElement {
        requests += request
        return next().single()
    }

    override suspend fun stream(request: ChatRequest, onEvent: suspend (JsonElement) -> Unit) {
        requests += request
        next().forEach { onEvent(it) }
    }

    private fun next(): List<JsonElement> = responses.removeFirst().getOrThrow()
}

class FakeServerDiscovery(private val servers: List<DiscoveredServer> = emptyList()) :
    ServerDiscovery {
    var searches = 0

    override fun search(): Flow<DiscoveredServer> {
        searches++
        return servers.asFlow()
    }
}

class FakeChatCreditsRepository(var credits: ChatCredits? = null) : ChatCreditsRepository {
    val requested = mutableListOf<Pair<String, String?>>()

    override suspend fun getCredits(itemId: String, seriesId: String?): ChatCredits? {
        requested += itemId to seriesId
        return credits
    }
}

/** Reverses the text: enough to see that nothing is stored in plain text. */
class ReversingCipher : SecretCipher {
    override fun encrypt(plain: String): String = plain.reversed()

    override fun decrypt(encrypted: String): String = encrypted.reversed()
}

class SettingsTestConnectivity(kind: NetworkKind = NetworkKind.Wifi) : Connectivity {
    override val networkKind = MutableStateFlow(kind)
    override val isServerReachable = MutableStateFlow(true)
    override val isOffline = MutableStateFlow(false)

    override suspend fun checkServer(): Boolean = isServerReachable.value
}

class SettingsTestPreferences : AppPreferences {
    override val ratingSource = MutableStateFlow(RatingSource.Imdb)
    override val quality =
        MutableStateFlow(
            listOf(NetworkKind.Local, NetworkKind.Wifi, NetworkKind.Cellular)
                .associateWith(QualityPreference::defaultFor),
        )

    override suspend fun setRatingSource(source: RatingSource) {
        ratingSource.value = source
    }

    override suspend fun setQuality(network: NetworkKind, preference: QualityPreference) {
        quality.update { it + (network to preference) }
    }
}
