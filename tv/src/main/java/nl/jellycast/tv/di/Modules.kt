package nl.jellycast.tv.di

import nl.jellycast.core.common.HomeSectionLeaves
import nl.jellycast.core.data.repository.TvProviderWatchNextRepository
import nl.jellycast.core.data.repository.WatchNextRepository
import nl.jellycast.core.di.coreModules
import nl.jellycast.core.model.AppInfo
import nl.jellycast.tv.BuildConfig
import nl.jellycast.tv.MainViewModel
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
import org.koin.android.ext.koin.androidContext
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
        single { HomeSectionLeaves() }
        single<WatchNextRepository> { TvProviderWatchNextRepository(androidContext(), get()) }
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

val jellyCastModules =
    coreModules(BuildConfig.VERSION_NAME, BuildConfig.DEV_TOOLS) + listOf(appModule, featureModules)
