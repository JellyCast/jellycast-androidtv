package nl.jellycast.mobile.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import nl.jellycast.core.di.APP_SCOPE
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.DataStoreAppPreferences
import nl.jellycast.mobile.feature.signin.LoginViewModel
import nl.jellycast.mobile.feature.signin.ServerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Owner: the settings worker (settings, sign-in, AI chat). */
val settingsModule =
    module {
        single<AppPreferences> {
            DataStoreAppPreferences(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("mobile_prefs")
                },
                get(named(APP_SCOPE)),
            )
        }
        viewModelOf(::ServerViewModel)
        viewModelOf(::LoginViewModel)
    }
