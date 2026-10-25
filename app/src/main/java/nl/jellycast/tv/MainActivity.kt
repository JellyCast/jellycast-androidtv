package nl.jellycast.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import nl.jellycast.tv.core.data.repository.DebugSegments
import nl.jellycast.tv.core.data.repository.parseDebugSegments
import nl.jellycast.tv.core.designsystem.component.BrandSplash
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.parsePlayLink
import nl.jellycast.tv.core.playback.FrameRateMode
import nl.jellycast.tv.core.playback.PlaybackSettingsStore
import nl.jellycast.tv.navigation.JellyCastNavHost
import org.koin.android.ext.android.get
import org.koin.compose.viewmodel.koinViewModel

private const val SPLASH_FADE_MS = 200

class MainActivity : ComponentActivity() {
    private var linkRequest by mutableStateOf<PlayRequest?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkRequest = intent.dataString?.let(::parsePlayLink)
    }

    override fun onStart() {
        super.onStart()
        // Device test hooks: `--es debugSegments "intro:5-60,outro:1650-1721"` (seconds),
        // `--ez debugMatchFrameRate false`, `--es debugPlayItem <id>`, `--el debugStartMs <ms>`.
        if (BuildConfig.DEV_TOOLS) {
            DebugSegments.override =
                intent?.getStringExtra("debugSegments")?.let(::parseDebugSegments)
        }
        if (BuildConfig.DEV_TOOLS && intent?.hasExtra("debugMatchFrameRate") == true) {
            get<PlaybackSettingsStore>().setFrameRateMode(
                if (intent.getBooleanExtra("debugMatchFrameRate", true)) {
                    FrameRateMode.Always
                } else {
                    FrameRateMode.Off
                },
            )
        }
    }

    private fun debugPlayItemId(): String? =
        if (BuildConfig.DEV_TOOLS) intent?.getStringExtra("debugPlayItem") else null

    private fun debugStartMs(): Long =
        if (BuildConfig.DEV_TOOLS) intent?.getLongExtra("debugStartMs", 0L) ?: 0L else 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) linkRequest = intent?.dataString?.let(::parsePlayLink)
        setContent {
            JellyCastTheme {
                val viewModel: MainViewModel = koinViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val authState by viewModel.authState.collectAsStateWithLifecycle()

                // Saveable: no second splash after a language switch or a recreated Activity.
                var splashDrawn by rememberSaveable { mutableStateOf(false) }
                var splashDone by rememberSaveable { mutableStateOf(false) }
                var splashSkipped by rememberSaveable { mutableStateOf(false) }
                val showSplash = !splashSkipped && !(splashDone && uiState is MainUiState.Ready)

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .onPreviewKeyEvent {
                            splashSkipped = true
                            false
                        },
                ) {
                    // Composing the start screen while the splash line draws makes it stutter.
                    val state = uiState
                    if (state is MainUiState.Ready && (splashDrawn || splashSkipped)) {
                        JellyCastNavHost(
                            startDestination = state.startDestination,
                            authState = authState,
                            debugPlayItemId = debugPlayItemId(),
                            debugStartMs = debugStartMs(),
                            linkRequest = linkRequest,
                            onLinkHandle = { linkRequest = null },
                        )
                    }
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(SPLASH_FADE_MS)),
                    ) {
                        // Focusable so a key press reaches (and skips) the splash before the
                        // start screen exists.
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        BrandSplash(
                            onDraw = { splashDrawn = true },
                            onFinish = { splashDone = true },
                            modifier = Modifier.focusRequester(focusRequester).focusable(),
                        )
                    }
                }
            }
        }
    }
}
