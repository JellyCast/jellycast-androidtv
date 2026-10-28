package nl.jellycast.mobile.di

import nl.jellycast.core.data.repository.DefaultDetailRepository
import nl.jellycast.core.data.repository.DetailRepository
import nl.jellycast.mobile.feature.movie.MovieViewModel
import nl.jellycast.mobile.feature.person.PersonViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Owner: the movie/info worker (movie, person, info panel). The info panel's own ViewModel is
 * created in `ItemInfoPanel`, keyed by its subject.
 */
val infoModule =
    module {
        singleOf(::DefaultDetailRepository) { bind<DetailRepository>() }
        viewModel { params -> MovieViewModel(params.get(), get()) }
        viewModel { params -> PersonViewModel(params.get(), get(), get()) }
    }
