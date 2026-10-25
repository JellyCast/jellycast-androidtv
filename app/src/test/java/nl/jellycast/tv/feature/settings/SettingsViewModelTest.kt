package nl.jellycast.tv.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.tv.core.model.AppInfo
import nl.jellycast.tv.core.model.AuthState
import nl.jellycast.tv.core.model.MovieView
import nl.jellycast.tv.core.playback.DEFAULT_MAX_BITRATE
import nl.jellycast.tv.core.playback.MaxBitrateOptions
import nl.jellycast.tv.core.playback.nextMaxBitrate
import nl.jellycast.tv.testing.Dutch
import nl.jellycast.tv.testing.English
import nl.jellycast.tv.testing.FakeLanguageRepository
import nl.jellycast.tv.testing.FakePlaybackSettingsStore
import nl.jellycast.tv.testing.FakeSessionRepository
import nl.jellycast.tv.testing.FakeUiPreferencesRepository
import nl.jellycast.tv.testing.MainDispatcherRule
import nl.jellycast.tv.testing.Spanish
import nl.jellycast.tv.testing.TestServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appInfo = AppInfo(versionName = "1.2.3", versionCode = 42)
    private val settingsStore = FakePlaybackSettingsStore()
    private val uiPreferences = FakeUiPreferencesRepository()
    private val languages = FakeLanguageRepository()

    private fun viewModel(
        repository: FakeSessionRepository =
            FakeSessionRepository(AuthState.LoggedIn(TestServer, "claude")),
    ) = SettingsViewModel(
        repository,
        appInfo,
        settingsStore,
        uiPreferences,
        languages,
    )

    @Test
    fun `shows version and account`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(
            SettingsUiState(
                versionName = "1.2.3",
                versionCode = 42,
                serverName = "home server",
                userName = "claude",
                language = Dutch,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `the language is set before the first collect`() {
        assertEquals(Dutch, viewModel().uiState.value.language)
    }

    @Test
    fun `quality cycles through the limits`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertNull(viewModel.uiState.value.maxBitrateMbps)
        val seen =
            (1..MaxBitrateOptions.size).map {
                viewModel.onMaxBitrateClick()
                viewModel.uiState.value.maxBitrateMbps
            }

        assertEquals(listOf(80, 40, 20, 10, null), seen)
        assertEquals(DEFAULT_MAX_BITRATE, settingsStore.settings.value.maxStreamingBitrate)
    }

    @Test
    fun `an unknown limit starts over at no limit`() {
        assertEquals(DEFAULT_MAX_BITRATE, nextMaxBitrate(12_345))
    }

    @Test
    fun `burn-in ASS toggles`() {
        val viewModel = viewModel()

        viewModel.onBurnInAssToggle()

        assertEquals(true, settingsStore.settings.value.burnInAss)
    }

    @Test
    fun `movie view toggles between detail and bookcase`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        assertEquals(MovieView.Detail, viewModel.uiState.value.movieView)

        viewModel.onMovieViewToggle()
        assertEquals(MovieView.Bookcase, uiPreferences.preferences.value.movieView)
        assertEquals(MovieView.Bookcase, viewModel.uiState.value.movieView)

        viewModel.onMovieViewToggle()
        assertEquals(MovieView.Detail, uiPreferences.preferences.value.movieView)
    }

    @Test
    fun `language cycles through the available languages`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val seen =
            (1..3).map {
                viewModel.onLanguageClick()
                viewModel.uiState.value.language
            }

        assertEquals(listOf(English, Spanish, Dutch), seen)
    }

    @Test
    fun `logout happens only once, also on a double press`() {
        val repository = FakeSessionRepository(AuthState.LoggedIn(TestServer, "claude"))
        val viewModel = viewModel(repository)

        viewModel.onLogout()
        viewModel.onLogout()

        assertEquals(1, repository.logoutCalls)
        assertEquals(AuthState.LoggedOut(TestServer), repository.auth.value)
    }
}
