package nl.jellycast.mobile.di

import nl.jellycast.core.data.repository.WatchNextRepository
import nl.jellycast.core.di.coreModules
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.WatchNextEntry
import nl.jellycast.mobile.BuildConfig
import nl.jellycast.mobile.MainViewModel
import org.koin.core.module.dsl.viewModelOf
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
        single<WatchNextRepository> { NoWatchNext }
        viewModelOf(::MainViewModel)
    }

/** One module per area, each in its own file, so parallel work never edits this list. */
val jellyCastModules =
    coreModules(BuildConfig.VERSION_NAME, BuildConfig.DEV_TOOLS) +
        listOf(
            appModule,
            libraryModule,
            homeModule,
            browseModule,
            infoModule,
            seriesModule,
            playerModule,
            downloadsModule,
            remoteModule,
            settingsModule,
        )

/** Phones have no Google TV home screen row. */
private object NoWatchNext : WatchNextRepository {
    override suspend fun publish(entries: List<WatchNextEntry>) = Unit

    override suspend fun clear() = Unit
}
