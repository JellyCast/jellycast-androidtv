package nl.jellycast.mobile.di

import nl.jellycast.core.di.APP_SCOPE
import nl.jellycast.mobile.core.library.DefaultLibraryStore
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.DefaultConnectivity
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Owner: the library worker. */
val libraryModule =
    module {
        single<LibraryStore> { DefaultLibraryStore(get(), get()) }
        single<Connectivity> { DefaultConnectivity(androidContext(), get(named(APP_SCOPE))) }
    }
