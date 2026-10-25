package nl.jellycast.tv.feature.login

import nl.jellycast.tv.core.model.QuickConnectState

data class LoginUiState(
    val serverName: String = "",
    val serverHost: String = "",
    val mode: LoginMode = LoginMode.QuickConnect,
    val quickConnect: QuickConnectState = QuickConnectState.Starting,
    val username: String = "",
    val password: String = "",
    val passwordStatus: PasswordStatus = PasswordStatus.Idle,
)

enum class LoginMode { QuickConnect, Password }

enum class PasswordStatus { Idle, Busy, InvalidCredentials, Unreachable }
