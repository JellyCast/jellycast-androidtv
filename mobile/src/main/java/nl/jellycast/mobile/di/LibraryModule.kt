package nl.jellycast.mobile.di

import java.io.File
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.repository.DefaultLibrarySyncRepository
import nl.jellycast.core.data.repository.DefaultServerStatusRepository
import nl.jellycast.core.data.repository.LibrarySyncRepository
import nl.jellycast.core.data.repository.ServerStatusRepository
import nl.jellycast.core.di.APP_SCOPE
import nl.jellycast.mobile.core.library.DefaultLibraryStore
import nl.jellycast.mobile.core.library.FileLibraryCache
import nl.jellycast.mobile.core.library.LibraryCache
import nl.jellycast.mobile.core.library.LibraryDiagnostics
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.DefaultConnectivity
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/** Owner: the library worker. */
val libraryModule =
    module {
        single<LibrarySyncRepository> { DefaultLibrarySyncRepository(get(), get(), get(), get()) }
        single<ServerStatusRepository> { DefaultServerStatusRepository(get(), get(), get()) }
        single<LibraryCache> {
            FileLibraryCache(
                File(androidContext().filesDir, "library.json"),
                get<AppDispatchers>().io,
            )
        }
        // The downloads area replaces this with the positions it kept while offline.
        single {
            DefaultLibraryStore(
                sync = get(),
                library = get(),
                session = get(),
                cache = get(),
                connectivity = get(),
                pendingProgress = get(),
                clock = get(),
                scope = get(named(APP_SCOPE)),
            )
        } binds arrayOf(LibraryStore::class, LibraryDiagnostics::class)
        single<Connectivity> {
            DefaultConnectivity(
                context = androidContext(),
                scope = get(named(APP_SCOPE)),
                io = get<AppDispatchers>().io,
                serverStatus = get(),
                session = get(),
            )
        }
    }
