package nl.jellycast.mobile.di

import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import nl.jellycast.core.di.APP_SCOPE
import nl.jellycast.mobile.core.remote.DataStoreRemoteDeviceStore
import nl.jellycast.mobile.core.remote.JellyfinRemoteSessionsApi
import nl.jellycast.mobile.core.remote.RemoteDeviceStore
import nl.jellycast.mobile.core.remote.RemoteSessions
import nl.jellycast.mobile.core.remote.RemoteSessionsApi
import nl.jellycast.mobile.core.remote.androidtv.AndroidKeystoreSealer
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvControl
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvLink
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvPairing
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvRemote
import nl.jellycast.mobile.core.remote.androidtv.ClientIdentitySource
import nl.jellycast.mobile.core.remote.androidtv.DefaultAndroidTvControl
import nl.jellycast.mobile.core.remote.androidtv.DefaultAndroidTvPairing
import nl.jellycast.mobile.core.remote.androidtv.FileClientIdentitySource
import nl.jellycast.mobile.core.remote.androidtv.KeySealer
import nl.jellycast.mobile.core.remote.androidtv.RemoteClientInfo
import nl.jellycast.mobile.feature.remote.PairTvViewModel
import nl.jellycast.mobile.feature.remote.PlayOnViewModel
import nl.jellycast.mobile.feature.remote.RemoteNowPlayingViewModel
import nl.jellycast.mobile.feature.remote.TvBarViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Owner: the remote worker. */
val remoteModule =
    module {
        single<RemoteSessionsApi> { JellyfinRemoteSessionsApi(get(), get(), get(), get()) }
        single<RemoteDeviceStore> {
            DataStoreRemoteDeviceStore(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("remote")
                },
            )
        }
        single<KeySealer> { AndroidKeystoreSealer() }
        single<ClientIdentitySource> {
            FileClientIdentitySource(
                File(androidContext().noBackupFilesDir, "androidtv"),
                get(),
                get(),
            )
        }
        single {
            val deviceId = get<ApiClient>().deviceInfo.id
            RemoteClientInfo(
                model = Build.MODEL,
                vendor = Build.MANUFACTURER,
                pairingName = "JellyCast ${Build.MODEL} ${deviceId.take(4).uppercase()}",
            )
        }
        single { AndroidTvRemote(get(), get(), get()) }
        single<AndroidTvLink> { get<AndroidTvRemote>() }
        single<AndroidTvPairing> { DefaultAndroidTvPairing(get(), get()) }
        factory<AndroidTvControl> { DefaultAndroidTvControl(get()) }
        single { RemoteSessions(get(), get(), get(), get(), get(named(APP_SCOPE))) }
        viewModelOf(::TvBarViewModel)
        viewModelOf(::RemoteNowPlayingViewModel)
        viewModelOf(::PlayOnViewModel)
        viewModelOf(::PairTvViewModel)
    }
