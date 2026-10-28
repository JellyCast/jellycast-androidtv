package nl.jellycast.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.AuthState
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.designsystem.theme.ProvideLayoutMetrics
import nl.jellycast.mobile.feature.signin.SignInScreen
import nl.jellycast.mobile.feature.splash.BrandSplash
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
                        // Once per launch, not after a rotation or a recreated Activity.
                        var splashDone by rememberSaveable { mutableStateOf(false) }
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
                        AnimatedVisibility(
                            visible = !splashDone,
                            enter = EnterTransition.None,
                            exit = fadeOut(tween(SPLASH_FADE_MS)),
                        ) {
                            BrandSplash(onFinish = { splashDone = true })
                        }
                    }
                }
            }
        }
    }
}

private const val SPLASH_FADE_MS = 200
