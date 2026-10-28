package nl.jellycast.mobile.di

import nl.jellycast.mobile.feature.player.PlayerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Owner: the player worker. */
val playerModule =
    module {
        viewModel { params -> PlayerViewModel(params.get(), params.get(), get(), get(), get()) }
    }
