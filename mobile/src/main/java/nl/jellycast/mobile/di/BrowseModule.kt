package nl.jellycast.mobile.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import nl.jellycast.core.data.repository.DefaultPeopleSearchRepository
import nl.jellycast.core.data.repository.DefaultPlaylistRepository
import nl.jellycast.core.data.repository.PeopleSearchRepository
import nl.jellycast.core.data.repository.PlaylistRepository
import nl.jellycast.core.di.APP_SCOPE
import nl.jellycast.mobile.feature.library.DataStoreRecentSearches
import nl.jellycast.mobile.feature.library.LibraryViewModel
import nl.jellycast.mobile.feature.library.RecentSearchStore
import nl.jellycast.mobile.feature.lists.AddToListViewModel
import nl.jellycast.mobile.feature.lists.ListViewModel
import nl.jellycast.mobile.feature.lists.ListsStore
import nl.jellycast.mobile.feature.lists.ListsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Owner: the library worker (Library tab, lists, action drawer). */
val browseModule =
    module {
        singleOf(::DefaultPlaylistRepository) { bind<PlaylistRepository>() }
        singleOf(::DefaultPeopleSearchRepository) { bind<PeopleSearchRepository>() }
        single<RecentSearchStore> {
            DataStoreRecentSearches(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("recent_searches")
                },
            )
        }
        single { ListsStore(get(), get(named(APP_SCOPE))) }
        viewModelOf(::LibraryViewModel)
        viewModelOf(::ListsViewModel)
        viewModelOf(::ListViewModel)
        viewModelOf(::AddToListViewModel)
    }
