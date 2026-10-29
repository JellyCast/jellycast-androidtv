package nl.jellycast.mobile.di

import nl.jellycast.mobile.feature.home.HomeViewModel
import nl.jellycast.mobile.feature.home.NothingDownloaded
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Owner: the home worker. */
val homeModule =
    module {
        viewModel { HomeViewModel(get(), get(), get(), getOrNull() ?: NothingDownloaded) }
    }
