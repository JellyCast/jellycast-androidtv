package nl.jellycast.mobile.feature.signin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Server, then sign-in. Owner: the settings worker (iOS signs in with Quick Connect only, with
 * server discovery on the same screen).
 */
@Composable
fun SignInScreen(hasServer: Boolean) {
    var choosingServer by rememberSaveable { mutableStateOf(!hasServer) }
    if (choosingServer) {
        ServerScreen(onNavigateToLogin = { choosingServer = false })
    } else {
        LoginScreen(onChangeServer = { choosingServer = true })
    }
}
