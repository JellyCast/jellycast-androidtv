package nl.jellycast.tv.core.data.repository

import kotlinx.coroutines.flow.Flow
import nl.jellycast.tv.core.model.AuthState
import nl.jellycast.tv.core.model.LoginResult
import nl.jellycast.tv.core.model.QuickConnectState
import nl.jellycast.tv.core.model.ServerCheckResult
import nl.jellycast.tv.core.model.ServerInfo

/** The only place that knows the access token. */
interface SessionRepository {
    val authState: Flow<AuthState>

    /** Reads the stored session and configures the API client; local storage only. */
    suspend fun restore(): AuthState

    /** Logs out when the server has revoked the token. */
    suspend fun validateSession()

    /**
     * UDP broadcast on the LAN. Emits each server once and completes after a few seconds; a
     * network error gives an empty flow.
     */
    fun discoverServers(): Flow<ServerInfo>

    suspend fun checkServer(input: String): ServerCheckResult

    suspend fun saveServer(server: ServerInfo)

    /** The login is saved by the time [QuickConnectState.Authenticated] is emitted. */
    fun quickConnect(): Flow<QuickConnectState>

    suspend fun loginWithPassword(username: String, password: String): LoginResult

    suspend fun logout()
}
