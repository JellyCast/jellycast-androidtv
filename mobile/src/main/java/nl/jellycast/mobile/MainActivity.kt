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
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.navigation.JellyCastNavHost
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
                val viewModel: MainViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val authState by viewModel.authState.collectAsStateWithLifecycle()
                // Surface sets the content color, so text is light on the dark background.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val state = uiState
                    if (state is MainUiState.Ready) {
                        JellyCastNavHost(
                            startDestination = state.startDestination,
                            authState = authState,
                        )
                    }
                }
            }
        }
    }
}
