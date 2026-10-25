package nl.jellycast.tv.core.data.repository

import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import nl.jellycast.tv.core.common.AppDispatchers
import nl.jellycast.tv.core.data.isSupportedServerVersion
import nl.jellycast.tv.core.data.normalizeServerAddress
import nl.jellycast.tv.core.data.toAuthState
import nl.jellycast.tv.core.data.toServerInfo
import nl.jellycast.tv.core.datastore.SessionStore
import nl.jellycast.tv.core.model.AuthState
import nl.jellycast.tv.core.model.LoginResult
import nl.jellycast.tv.core.model.QuickConnectState
import nl.jellycast.tv.core.model.ServerCheckResult
import nl.jellycast.tv.core.model.ServerInfo
import nl.jellycast.tv.core.network.ServerCheckHttpOptions
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber

private val QUICK_CONNECT_POLL_INTERVAL = 3.seconds
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

// The SDK waits up to the timeout per read and reads maxServers + 1 times, so a search without
// servers takes 4 × 500 ms = 2 s.
private const val DISCOVERY_TIMEOUT_MS = 500
private const val DISCOVERY_MAX_SERVERS = 3

class DefaultSessionRepository(
    private val jellyfin: Jellyfin,
    private val api: ApiClient,
    private val store: SessionStore,
    private val watchNext: WatchNextRepository,
    private val dispatchers: AppDispatchers,
) : SessionRepository {
    override val authState: Flow<AuthState> = store.session.map { it.toAuthState() }

    override suspend fun restore(): AuthState = withContext(dispatchers.io) {
        val session = store.session.first()
        api.update(baseUrl = session.serverUrl, accessToken = session.accessToken)
        session.toAuthState()
    }

    override suspend fun validateSession() {
        withContext(dispatchers.io) {
            if (api.accessToken == null) return@withContext
            try {
                val user = api.userApi.getCurrentUser().content
                user.name?.let { store.saveUser(it, user.preferredLanguages()) }
            } catch (e: InvalidStatusException) {
                if (e.status == HTTP_UNAUTHORIZED) {
                    Timber.i("Token revoked by the server; logging out")
                    clearLogin()
                }
            } catch (e: ApiClientException) {
                // Server temporarily unreachable: stay logged in; screens show their own error.
                Timber.w(e, "Could not validate session")
            }
        }
    }

    override fun discoverServers(): Flow<ServerInfo> = jellyfin.discovery
        .discoverLocalServers(timeout = DISCOVERY_TIMEOUT_MS, maxServers = DISCOVERY_MAX_SERVERS)
        .mapNotNull { it.toServerInfo() }
        .catch { e ->
            if (e !is IOException) throw e
            Timber.w(e, "Server discovery failed")
        }.flowOn(dispatchers.io)

    override suspend fun checkServer(input: String): ServerCheckResult {
        val url = normalizeServerAddress(input) ?: return ServerCheckResult.InvalidAddress
        return withContext(dispatchers.io) {
            val client = jellyfin.createApi(
                baseUrl = url,
                httpClientOptions = ServerCheckHttpOptions,
            )
            try {
                val info = client.systemApi.getPublicSystemInfo().content
                val version = info.version.orEmpty()
                when {
                    info.productName?.contains("Jellyfin", ignoreCase = true) != true ->
                        ServerCheckResult.NotJellyfin

                    !isSupportedServerVersion(version) ->
                        ServerCheckResult.UnsupportedVersion(version)

                    else ->
                        ServerCheckResult.Success(
                            server = ServerInfo(url = url, name = info.serverName ?: url),
                            version = version,
                        )
                }
            } catch (e: InvalidStatusException) {
                when (e.status) {
                    HTTP_NOT_FOUND -> ServerCheckResult.NotJellyfin
                    else -> ServerCheckResult.Unreachable
                }
            } catch (_: InvalidContentException) {
                ServerCheckResult.NotJellyfin
            } catch (e: ApiClientException) {
                Timber.d(e, "Server unreachable: %s", url)
                ServerCheckResult.Unreachable
            }
        }
    }

    override suspend fun saveServer(server: ServerInfo) {
        withContext(dispatchers.io) {
            store.saveServer(url = server.url, name = server.name)
            api.update(baseUrl = server.url, accessToken = null)
        }
    }

    override fun quickConnect(): Flow<QuickConnectState> = flow {
        emit(QuickConnectState.Starting)
        val serverUrl =
            requireNotNull(store.session.first().serverUrl) { "No server selected" }
        val client = jellyfin.createApi(baseUrl = serverUrl)
        if (!client.quickConnectApi.getQuickConnectEnabled().content) {
            emit(QuickConnectState.Disabled)
            return@flow
        }
        // Codes expire after a few minutes (the server then answers 404): request a new one.
        while (true) {
            var result = client.quickConnectApi.initiateQuickConnect().content
            emit(QuickConnectState.WaitingForApproval(result.code))
            while (!result.authenticated) {
                delay(QUICK_CONNECT_POLL_INTERVAL)
                result =
                    try {
                        client.quickConnectApi.getQuickConnectState(result.secret).content
                    } catch (e: InvalidStatusException) {
                        if (e.status == HTTP_NOT_FOUND) break else throw e
                    }
            }
            if (result.authenticated) {
                val auth =
                    client.userApi
                        .authenticateWithQuickConnect(QuickConnectDto(secret = result.secret))
                        .content
                saveLogin(serverUrl, auth)
                emit(QuickConnectState.Authenticated)
                return@flow
            }
        }
    }.catch { e ->
        if (e !is ApiClientException) throw e
        Timber.w(e, "Quick Connect failed")
        emit(QuickConnectState.Failed)
    }.flowOn(dispatchers.io)

    override suspend fun loginWithPassword(username: String, password: String): LoginResult =
        withContext(dispatchers.io) {
            val serverUrl =
                requireNotNull(store.session.first().serverUrl) { "No server selected" }
            val client = jellyfin.createApi(baseUrl = serverUrl)
            try {
                val auth =
                    client.userApi
                        .authenticateUserByName(
                            AuthenticateUserByName(username = username, pw = password),
                        )
                        .content
                saveLogin(serverUrl, auth)
                LoginResult.Success
            } catch (e: InvalidStatusException) {
                when (e.status) {
                    HTTP_UNAUTHORIZED -> LoginResult.InvalidCredentials
                    else -> LoginResult.Unreachable
                }
            } catch (e: ApiClientException) {
                Timber.w(e, "Login failed")
                LoginResult.Unreachable
            }
        }

    override suspend fun logout() {
        withContext(dispatchers.io) {
            try {
                api.sessionApi.reportSessionEnded()
            } catch (e: ApiClientException) {
                // Log out locally even when the server does not respond.
                Timber.w(e, "Could not notify the server of the logout")
            }
            clearLogin()
        }
        watchNext.clear()
    }

    private suspend fun saveLogin(serverUrl: String, auth: AuthenticationResult) {
        val token = requireNotNull(auth.accessToken) { "Server returned no access token" }
        val user = requireNotNull(auth.user) { "Server returned no user" }
        store.saveLogin(
            userId = user.id.toString(),
            userName = user.name.orEmpty(),
            accessToken = token,
            preferredLanguages = user.preferredLanguages(),
        )
        api.update(baseUrl = serverUrl, accessToken = token)
    }

    private fun UserDto.preferredLanguages(): List<String> = listOfNotNull(
        configuration?.subtitleLanguagePreference,
        configuration?.audioLanguagePreference,
    ).filter { it.isNotBlank() }.distinct()

    private suspend fun clearLogin() {
        store.clearLogin()
        api.update(accessToken = null)
    }
}
