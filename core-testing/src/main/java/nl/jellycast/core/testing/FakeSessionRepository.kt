package nl.jellycast.core.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.LoginResult
import nl.jellycast.core.model.QuickConnectState
import nl.jellycast.core.model.ServerCheckResult
import nl.jellycast.core.model.ServerInfo

val TestServer = ServerInfo(url = "http://192.168.1.4:8096", name = "home server")

class FakeSessionRepository(initial: AuthState = AuthState.NoServer) : SessionRepository {
    val auth = MutableStateFlow(initial)
    override val authState: Flow<AuthState> = auth

    var checkServerResult: ServerCheckResult = ServerCheckResult.Success(TestServer, "10.11.11")

    // Makes `checkServer` wait until the test calls `complete(Unit)`.
    var checkServerGate: CompletableDeferred<Unit>? = null
    val checkedInputs = mutableListOf<String>()

    var quickConnectFlow: Flow<QuickConnectState> = emptyFlow()
    var quickConnectStarts = 0
    var loginResult: LoginResult = LoginResult.Success
    var validateCalls = 0
    var logoutCalls = 0

    override suspend fun restore(): AuthState = auth.value

    override suspend fun validateSession() {
        validateCalls++
    }

    var discoveryFlow: Flow<ServerInfo> = emptyFlow()
    var discoveryStarts = 0

    override fun discoverServers(): Flow<ServerInfo> {
        discoveryStarts++
        return discoveryFlow
    }

    override suspend fun checkServer(input: String): ServerCheckResult {
        checkedInputs += input
        checkServerGate?.await()
        return checkServerResult
    }

    override suspend fun saveServer(server: ServerInfo) {
        auth.value = AuthState.LoggedOut(server)
    }

    override fun quickConnect(): Flow<QuickConnectState> {
        quickConnectStarts++
        return quickConnectFlow
    }

    override suspend fun loginWithPassword(username: String, password: String): LoginResult {
        val success = loginResult == LoginResult.Success
        if (success) auth.value = AuthState.LoggedIn(TestServer, username)
        return loginResult
    }

    override suspend fun logout() {
        logoutCalls++
        val current = auth.value
        if (current is AuthState.LoggedIn) auth.value = AuthState.LoggedOut(current.server)
    }
}
