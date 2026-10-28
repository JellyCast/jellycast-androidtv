package nl.jellycast.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.AudioPassthrough
import nl.jellycast.core.data.ResourceGenreNames
import nl.jellycast.core.data.repository.DefaultLanguageRepository
import nl.jellycast.core.data.repository.DefaultLibraryRepository
import nl.jellycast.core.data.repository.DefaultPlaybackRepository
import nl.jellycast.core.data.repository.DefaultRemoteControlRepository
import nl.jellycast.core.data.repository.DefaultSessionRepository
import nl.jellycast.core.data.repository.DefaultUiPreferencesRepository
import nl.jellycast.core.data.repository.LanguageRepository
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.data.repository.OriginalLanguageRepository
import nl.jellycast.core.data.repository.PlaybackRepository
import nl.jellycast.core.data.repository.RemoteControlRepository
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.data.repository.UiPreferencesRepository
import nl.jellycast.core.data.repository.WikidataOriginalLanguageRepository
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.datastore.UiPreferencesStore
import nl.jellycast.core.model.GenreNames
import nl.jellycast.core.network.ImageUrlBuilder
import nl.jellycast.core.network.JellyfinImageUrlBuilder
import nl.jellycast.core.network.createImageLoader
import nl.jellycast.core.network.createJellyfinInstance
import nl.jellycast.core.playback.AndroidCapabilitiesReader
import nl.jellycast.core.playback.CapabilitiesReader
import nl.jellycast.core.playback.DefaultPlaybackSettingsStore
import nl.jellycast.core.playback.DefaultPlaybackTechProvider
import nl.jellycast.core.playback.PlaybackPlanner
import nl.jellycast.core.playback.PlaybackPreparer
import nl.jellycast.core.playback.PlaybackReporter
import nl.jellycast.core.playback.PlaybackSettingsStore
import nl.jellycast.core.playback.PlaybackStartLog
import nl.jellycast.core.playback.PlaybackTechProvider
import nl.jellycast.core.playback.PlayerFactory
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Everything both apps share. The app adds its own `WatchNextRepository`, `AppInfo` and
 * ViewModels.
 */
fun coreModules(versionName: String, devTools: Boolean): List<Module> = listOf(
    commonModule,
    networkModule(versionName, devTools),
    dataModule,
    playbackModule(devTools),
)

private val commonModule =
    module {
        single { AppDispatchers(io = Dispatchers.IO) }
        single { Clock(System::currentTimeMillis) }
        single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    }

private fun networkModule(versionName: String, devTools: Boolean) = module {
    single { createJellyfinInstance(androidContext(), versionName) }
    single { get<Jellyfin>().createApi() }
    singleOf(::JellyfinImageUrlBuilder) { bind<ImageUrlBuilder>() }
    single { createImageLoader(androidContext(), enableLogging = devTools) }
}

private val dataModule =
    module {
        single {
            SessionStore(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("session")
                },
            )
        }
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

private fun playbackModule(devTools: Boolean) = module {
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
    single { PlayerFactory(androidContext(), get(), enableEventLogging = devTools) }
}

/** The app-wide coroutine scope, for work that must outlive a screen. */
const val APP_SCOPE = "appScope"
