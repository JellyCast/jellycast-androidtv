package nl.jellycast.tv.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.jellycast.tv.BuildConfig
import nl.jellycast.tv.MainViewModel
import nl.jellycast.tv.core.common.AppDispatchers
import nl.jellycast.tv.core.common.Clock
import nl.jellycast.tv.core.common.HomeSectionLeaves
import nl.jellycast.tv.core.data.AudioPassthrough
import nl.jellycast.tv.core.data.ResourceGenreNames
import nl.jellycast.tv.core.data.repository.DefaultLanguageRepository
import nl.jellycast.tv.core.data.repository.DefaultLibraryRepository
import nl.jellycast.tv.core.data.repository.DefaultPlaybackRepository
import nl.jellycast.tv.core.data.repository.DefaultRemoteControlRepository
import nl.jellycast.tv.core.data.repository.DefaultSessionRepository
import nl.jellycast.tv.core.data.repository.DefaultUiPreferencesRepository
import nl.jellycast.tv.core.data.repository.LanguageRepository
import nl.jellycast.tv.core.data.repository.LibraryRepository
import nl.jellycast.tv.core.data.repository.OriginalLanguageRepository
import nl.jellycast.tv.core.data.repository.PlaybackRepository
import nl.jellycast.tv.core.data.repository.RemoteControlRepository
import nl.jellycast.tv.core.data.repository.SessionRepository
import nl.jellycast.tv.core.data.repository.TvProviderWatchNextRepository
import nl.jellycast.tv.core.data.repository.UiPreferencesRepository
import nl.jellycast.tv.core.data.repository.WatchNextRepository
import nl.jellycast.tv.core.data.repository.WikidataOriginalLanguageRepository
import nl.jellycast.tv.core.datastore.SessionStore
import nl.jellycast.tv.core.datastore.UiPreferencesStore
import nl.jellycast.tv.core.model.AppInfo
import nl.jellycast.tv.core.model.GenreNames
import nl.jellycast.tv.core.network.ImageUrlBuilder
import nl.jellycast.tv.core.network.JellyfinImageUrlBuilder
import nl.jellycast.tv.core.network.createImageLoader
import nl.jellycast.tv.core.network.createJellyfinInstance
import nl.jellycast.tv.core.playback.AndroidCapabilitiesReader
import nl.jellycast.tv.core.playback.CapabilitiesReader
import nl.jellycast.tv.core.playback.DefaultPlaybackSettingsStore
import nl.jellycast.tv.core.playback.DefaultPlaybackTechProvider
import nl.jellycast.tv.core.playback.PlaybackPlanner
import nl.jellycast.tv.core.playback.PlaybackPreparer
import nl.jellycast.tv.core.playback.PlaybackReporter
import nl.jellycast.tv.core.playback.PlaybackSettingsStore
import nl.jellycast.tv.core.playback.PlaybackStartLog
import nl.jellycast.tv.core.playback.PlaybackTechProvider
import nl.jellycast.tv.core.playback.PlayerFactory
import nl.jellycast.tv.feature.detail.MovieDetailViewModel
import nl.jellycast.tv.feature.home.HomeSuggestionViewModel
import nl.jellycast.tv.feature.home.HomeViewModel
import nl.jellycast.tv.feature.library.LibraryViewModel
import nl.jellycast.tv.feature.login.LoginViewModel
import nl.jellycast.tv.feature.movies.MoviesViewModel
import nl.jellycast.tv.feature.player.PlayerViewModel
import nl.jellycast.tv.feature.series.SeriesDetailViewModel
import nl.jellycast.tv.feature.series.SeriesViewModel
import nl.jellycast.tv.feature.server.ServerViewModel
import nl.jellycast.tv.feature.settings.PlaybackSettingsViewModel
import nl.jellycast.tv.feature.settings.SettingsViewModel
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule =
    module {
        single {
            AppInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                devServerUrl = BuildConfig.DEV_SERVER_URL,
            )
        }
        single { AppDispatchers(io = Dispatchers.IO) }
        single { Clock(System::currentTimeMillis) }
        single { HomeSectionLeaves() }
    }

val networkModule =
    module {
        single { createJellyfinInstance(androidContext(), BuildConfig.VERSION_NAME) }
        single { get<Jellyfin>().createApi() }
        singleOf(::JellyfinImageUrlBuilder) { bind<ImageUrlBuilder>() }
        single { createImageLoader(androidContext(), enableLogging = BuildConfig.DEV_TOOLS) }
    }

val dataModule =
    module {
        single {
            SessionStore(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("session")
                },
            )
        }
        single<WatchNextRepository> { TvProviderWatchNextRepository(androidContext(), get()) }
        singleOf(::DefaultSessionRepository) { bind<SessionRepository>() }
        single {
            UiPreferencesStore(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("ui")
                },
            )
        }
        singleOf(::DefaultUiPreferencesRepository) { bind<UiPreferencesRepository>() }
        singleOf(::DefaultLibraryRepository) { bind<LibraryRepository>() }
        single<RemoteControlRepository> {
            DefaultRemoteControlRepository(get(), OkHttpClient(), get(), get(named(APP_SCOPE)))
        }
        single<LanguageRepository> { DefaultLanguageRepository(androidContext()) }
        single<GenreNames> { ResourceGenreNames(androidContext(), get()) }
    }

val playbackModule =
    module {
        single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        singleOf(::DefaultPlaybackRepository) { bind<PlaybackRepository>() }
        singleOf(::WikidataOriginalLanguageRepository) { bind<OriginalLanguageRepository>() }
        singleOf(::AndroidCapabilitiesReader) { bind<CapabilitiesReader>() }
        single { AudioPassthrough { get<CapabilitiesReader>().read().passthroughAudio } }
        single<PlaybackSettingsStore> {
            DefaultPlaybackSettingsStore(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("playback")
                },
                get(named(APP_SCOPE)),
            )
        }
        factoryOf(::PlaybackPreparer) { bind<PlaybackPlanner>() }
        factoryOf(::DefaultPlaybackTechProvider) { bind<PlaybackTechProvider>() }
        single { PlaybackReporter(get(), get(named(APP_SCOPE))) }
        single {
            // The external app folder is readable over adb even for release builds.
            val dir = androidContext().getExternalFilesDir(null) ?: androidContext().filesDir
            PlaybackStartLog(
                File(dir, "playback-starts.txt"),
                get(named(APP_SCOPE)),
                get<AppDispatchers>().io,
                get(),
            )
        }
        single {
            PlayerFactory(androidContext(), get(), enableEventLogging = BuildConfig.DEV_TOOLS)
        }
    }

val featureModules =
    module {
        viewModelOf(::MainViewModel)
        viewModelOf(::ServerViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::SeriesViewModel)
        viewModelOf(::MoviesViewModel)
        viewModelOf(::LibraryViewModel)
        viewModelOf(::HomeSuggestionViewModel)
        viewModel { params -> MovieDetailViewModel(params.get(), get(), get()) }
        viewModel { params -> SeriesDetailViewModel(params.get(), get()) }
        viewModelOf(::SettingsViewModel)
        viewModelOf(::PlaybackSettingsViewModel)
        viewModel { params ->
            PlayerViewModel(params.get(), params.get(), params.get(), get(), get(), get())
        }
    }

val jellyCastModules = listOf(appModule, networkModule, dataModule, playbackModule, featureModules)

private const val APP_SCOPE = "appScope"
