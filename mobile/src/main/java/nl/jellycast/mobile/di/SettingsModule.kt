package nl.jellycast.mobile.di

import android.content.Context
import android.media.AudioManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import java.util.concurrent.TimeUnit
import nl.jellycast.core.data.repository.ChatCreditsRepository
import nl.jellycast.core.data.repository.DefaultChatCreditsRepository
import nl.jellycast.core.di.APP_SCOPE
import nl.jellycast.mobile.core.chat.ChatClientFactory
import nl.jellycast.mobile.core.chat.ChatHttp
import nl.jellycast.mobile.core.chat.ChatSettings
import nl.jellycast.mobile.core.chat.DataStoreChatSettings
import nl.jellycast.mobile.core.chat.KeystoreSecretCipher
import nl.jellycast.mobile.core.chat.ModelMemory
import nl.jellycast.mobile.core.chat.OkHttpChatHttp
import nl.jellycast.mobile.core.chat.SecretCipher
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.AudioOutputDelay
import nl.jellycast.mobile.core.settings.DataStoreAppPreferences
import nl.jellycast.mobile.core.settings.SystemAudioOutputDelay
import nl.jellycast.mobile.feature.settings.AiChatSettingsViewModel
import nl.jellycast.mobile.feature.settings.ServerAddressesViewModel
import nl.jellycast.mobile.feature.settings.SettingsViewModel
import nl.jellycast.mobile.feature.signin.ServerDiscovery
import nl.jellycast.mobile.feature.signin.SignInViewModel
import nl.jellycast.mobile.feature.signin.UdpServerDiscovery
import nl.jellycast.mobile.ui.chat.ChatViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

private const val CHAT_TIMEOUT_SECONDS = 120L

/** Owner: the settings worker (settings, sign-in, AI chat). */
val settingsModule =
    module {
        single<AudioOutputDelay>(createdAtStart = true) {
            SystemAudioOutputDelay(
                androidContext().getSystemService(AudioManager::class.java),
                androidContext().getSharedPreferences("audio_delay", Context.MODE_PRIVATE),
            )
        }
        single<AppPreferences> {
            DataStoreAppPreferences(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("mobile_prefs")
                },
                get(named(APP_SCOPE)),
            )
        }
        single<SecretCipher> { KeystoreSecretCipher() }
        single<ServerDiscovery> { UdpServerDiscovery(get()) }
        viewModel { SignInViewModel(get(), get(), get(), get()) }
        viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }

        single {
            DataStoreChatSettings(
                PreferenceDataStoreFactory.create {
                    androidContext().preferencesDataStoreFile("chat_prefs")
                },
                get(),
                get(named(APP_SCOPE)),
            )
        } binds arrayOf(ChatSettings::class, ModelMemory::class)
        single<ChatHttp> {
            OkHttpChatHttp(
                OkHttpClient
                    .Builder()
                    .readTimeout(CHAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .callTimeout(0, TimeUnit.SECONDS)
                    .build(),
                get(),
            )
        }
        single { ChatClientFactory(get(), get(), get()) }
        single<ChatCreditsRepository> { DefaultChatCreditsRepository(get(), get(), get()) }
        viewModel { ChatViewModel(get(), get(), get(), get()) }
        viewModel { AiChatSettingsViewModel(get(), get()) }
        viewModel { ServerAddressesViewModel(get(), get(), get()) }
    }
