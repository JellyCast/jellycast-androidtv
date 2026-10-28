package nl.jellycast.mobile.di

import nl.jellycast.core.data.repository.WatchNextRepository
import nl.jellycast.core.di.coreModules
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.WatchNextEntry
import nl.jellycast.mobile.BuildConfig
import nl.jellycast.mobile.MainViewModel
import nl.jellycast.mobile.feature.detail.MovieDetailViewModel
import nl.jellycast.mobile.feature.home.HomeViewModel
import nl.jellycast.mobile.feature.login.LoginViewModel
import nl.jellycast.mobile.feature.player.PlayerViewModel
import nl.jellycast.mobile.feature.series.SeriesDetailViewModel
import nl.jellycast.mobile.feature.server.ServerViewModel
import nl.jellycast.mobile.feature.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
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
    }

val featureModules =
    module {
        viewModelOf(::MainViewModel)
        viewModelOf(::ServerViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::SettingsViewModel)
        viewModel { params -> MovieDetailViewModel(params.get(), get()) }
        viewModel { params -> SeriesDetailViewModel(params.get(), get()) }
        viewModel { params -> PlayerViewModel(params.get(), params.get(), get(), get(), get()) }
    }

val jellyCastModules =
    coreModules(BuildConfig.VERSION_NAME, BuildConfig.DEV_TOOLS) + listOf(appModule, featureModules)

/** Phones have no Google TV home screen row. */
private object NoWatchNext : WatchNextRepository {
    override suspend fun publish(entries: List<WatchNextEntry>) = Unit

    override suspend fun clear() = Unit
}
