package nl.jellycast.mobile.di

import nl.jellycast.mobile.feature.series.SeriesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Owner: the series worker. */
val seriesModule =
    module {
        viewModelOf(::SeriesViewModel)
    }
