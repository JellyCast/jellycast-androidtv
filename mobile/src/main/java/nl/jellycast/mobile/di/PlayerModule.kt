package nl.jellycast.mobile.di

import nl.jellycast.core.data.repository.DefaultMediaSourceRepository
import nl.jellycast.core.data.repository.MediaSourceRepository
import nl.jellycast.core.playback.TranscodePlanner
import nl.jellycast.core.playback.TranscodePreparer
import nl.jellycast.mobile.feature.player.NowPlayingViewModel
import nl.jellycast.mobile.feature.player.PlaybackReports
import nl.jellycast.mobile.feature.player.ServerPlaybackReports
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Owner: the player worker. */
val playerModule =
    module {
        singleOf(::DefaultMediaSourceRepository) { bind<MediaSourceRepository>() }
        factoryOf(::TranscodePreparer) { bind<TranscodePlanner>() }
        singleOf(::ServerPlaybackReports) { bind<PlaybackReports>() }
        viewModelOf(::NowPlayingViewModel)
    }
