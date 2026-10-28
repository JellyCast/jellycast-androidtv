package nl.jellycast.mobile.core.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ChatProvider(val title: String, val keyPlaceholder: String) {
    Claude("Claude (Anthropic)", "sk-ant-…"),
    OpenAi("ChatGPT (OpenAI)", "sk-…"),
}

data class ChatSettingsState(
    val provider: ChatProvider = ChatProvider.Claude,
    val avoidsSpoilers: Boolean = true,
    val searchesWeb: Boolean = true,
    /** "sk-ant-•••• 4f2a" for the chosen provider's key; `null` without a key. */
    val maskedKey: String? = null,
) {
    val hasKey: Boolean get() = maskedKey != null
}

/** Which AI service the chat uses and its API key. Without a key there is no Chat tab. */
interface ChatSettings {
    val state: StateFlow<ChatSettingsState>

    /** The chosen provider with its key, decrypted; `null` without a key. */
    suspend fun credentials(): ChatCredentials?

    suspend fun setProvider(provider: ChatProvider)

    suspend fun setAvoidsSpoilers(avoids: Boolean)

    suspend fun setSearchesWeb(searches: Boolean)

    /** Stores the key for the chosen provider; blank or `null` removes it. */
    suspend fun setKey(key: String?)
}

class ChatCredentials(val provider: ChatProvider, val apiKey: String) {
    override fun toString(): String = "ChatCredentials($provider)"
}

/** Encrypts the API keys at rest. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** `null` when the text can't be decrypted (another device, a reset Keystore). */
    fun decrypt(encrypted: String): String?
}

fun maskKey(key: String): String =
    "${key.take(MASK_PREFIX)}•••• ${key.takeLast(MASK_SUFFIX)}"

private const val MASK_PREFIX = 7
private const val MASK_SUFFIX = 4

class DataStoreChatSettings(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
    scope: CoroutineScope,
) : ChatSettings,
    ModelMemory {
    override val state: StateFlow<ChatSettingsState> =
        store.data
            .map { prefs ->
                val provider = prefs.provider()
                ChatSettingsState(
                    provider = provider,
                    avoidsSpoilers = prefs[SpoilersKey] ?: true,
                    searchesWeb = prefs[SearchKey] ?: true,
                    maskedKey = prefs.key(provider)?.let(::maskKey),
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, ChatSettingsState())

    override suspend fun credentials(): ChatCredentials? {
        val prefs = store.data.first()
        val provider = prefs.provider()
        return prefs.key(provider)?.let { ChatCredentials(provider, it) }
    }

    override suspend fun setProvider(provider: ChatProvider) {
        store.edit { it[ProviderKey] = provider.name }
    }

    override suspend fun setAvoidsSpoilers(avoids: Boolean) {
        store.edit { it[SpoilersKey] = avoids }
    }

    override suspend fun setSearchesWeb(searches: Boolean) {
        store.edit { it[SearchKey] = searches }
    }

    override suspend fun setKey(key: String?) {
        val trimmed = key?.trim()?.takeIf { it.isNotEmpty() }
        store.edit { prefs ->
            val name = keyName(prefs.provider())
            if (trimmed == null) prefs.remove(name) else prefs[name] = cipher.encrypt(trimmed)
        }
    }

    override suspend fun storedModel(): String? = store.data.first()[OpenAiModelKey]

    override suspend fun storeModel(model: String) {
        store.edit { it[OpenAiModelKey] = model }
    }

    private fun Preferences.provider(): ChatProvider =
        ChatProvider.entries.firstOrNull { it.name == this[ProviderKey] } ?: ChatProvider.Claude

    private fun Preferences.key(provider: ChatProvider): String? =
        this[keyName(provider)]?.let(cipher::decrypt)
}

private val ProviderKey = stringPreferencesKey("provider")
private val SpoilersKey = booleanPreferencesKey("avoids_spoilers")
private val SearchKey = booleanPreferencesKey("searches_web")
private val OpenAiModelKey = stringPreferencesKey("openai_model")

private fun keyName(provider: ChatProvider) =
    stringPreferencesKey("api_key_${provider.name.lowercase()}")

/** Builds the client for the chosen provider, or `null` without a key. */
class ChatClientFactory(
    private val settings: ChatSettings,
    private val http: ChatHttp,
    private val memory: ModelMemory,
) {
    suspend fun client(): ChatClient? {
        val credentials = settings.credentials() ?: return null
        return when (credentials.provider) {
            ChatProvider.Claude -> ClaudeChatClient(credentials.apiKey, http)
            ChatProvider.OpenAi -> OpenAiChatClient(credentials.apiKey, http, memory)
        }
    }
}
