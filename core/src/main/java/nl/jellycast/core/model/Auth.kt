package nl.jellycast.core.model

data class ServerInfo(val url: String, val name: String)

/** Never contains the access token. */
sealed interface AuthState {
    data object NoServer : AuthState

    data class LoggedOut(val server: ServerInfo) : AuthState

    data class LoggedIn(val server: ServerInfo, val userName: String) : AuthState
}

sealed interface ServerCheckResult {
    data class Success(val server: ServerInfo, val version: String) : ServerCheckResult

    data object InvalidAddress : ServerCheckResult

    data object Unreachable : ServerCheckResult

    data object NotJellyfin : ServerCheckResult

    data class UnsupportedVersion(val version: String) : ServerCheckResult
}

sealed interface QuickConnectState {
    data object Starting : QuickConnectState

    data class WaitingForApproval(val code: String) : QuickConnectState

    data object Disabled : QuickConnectState

    data object Failed : QuickConnectState

    data object Authenticated : QuickConnectState
}

enum class LoginResult { Success, InvalidCredentials, Unreachable }
