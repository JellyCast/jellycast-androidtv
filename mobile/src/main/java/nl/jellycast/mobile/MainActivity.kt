package nl.jellycast.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.AuthState
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.designsystem.theme.ProvideLayoutMetrics
import nl.jellycast.mobile.feature.signin.SignInScreen
import nl.jellycast.mobile.navigation.MainShell
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, whatever the system theme: light status bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            JellyCastTheme {
                // Surface sets the content color, so text is light on the dark background.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ProvideLayoutMetrics {
                        val viewModel: MainViewModel = koinViewModel()
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        val authState by viewModel.authState.collectAsStateWithLifecycle()
                        if (uiState is MainUiState.Ready) {
                            // Follows the session: signing in opens the app, signing out (or a
                            // revoked token) returns to sign-in.
                            val signedIn =
                                authState?.let { it is AuthState.LoggedIn }
                                    ?: (uiState as MainUiState.Ready).signedIn
                            if (signedIn) {
                                MainShell()
                            } else {
                                SignInScreen(hasServer = authState !is AuthState.NoServer)
                            }
                        }
                    }
                }
            }
        }
    }
}
