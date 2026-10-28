package nl.jellycast.mobile.di

import android.content.Context
import java.io.File
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.repository.DefaultDownloadRepository
import nl.jellycast.core.data.repository.DownloadRepository
import nl.jellycast.core.di.APP_SCOPE
import nl.jellycast.mobile.core.downloads.CompactDownloader
import nl.jellycast.mobile.core.downloads.DownloadFiles
import nl.jellycast.mobile.core.downloads.DownloadForeground
import nl.jellycast.mobile.core.downloads.DownloadSettings
import nl.jellycast.mobile.core.downloads.DownloadStore
import nl.jellycast.mobile.core.downloads.OfflineProgress
import nl.jellycast.mobile.core.downloads.OriginalDownloader
import nl.jellycast.mobile.core.downloads.ServiceDownloadForeground
import nl.jellycast.mobile.core.downloads.SharedPreferencesDownloadSettings
import nl.jellycast.mobile.core.downloads.StatFsStorageSpace
import nl.jellycast.mobile.core.downloads.StorageSpace
import nl.jellycast.mobile.core.library.PendingProgress
import nl.jellycast.mobile.feature.downloads.DownloadedSeriesViewModel
import nl.jellycast.mobile.feature.downloads.DownloadsViewModel
import nl.jellycast.mobile.ui.downloads.LocalDownloads
import nl.jellycast.mobile.ui.downloads.OfflineAvailability
import nl.jellycast.mobile.ui.downloads.OfflinePositions
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Owner: the downloads worker. */
val downloadsModule =
    module {
        single { DownloadFiles(File(androidContext().filesDir, "downloads")) }
        single<DownloadRepository> {
            DefaultDownloadRepository(get(), get(), OkHttpClient(), get<AppDispatchers>())
        }
        single<DownloadSettings> {
            SharedPreferencesDownloadSettings(
                androidContext().getSharedPreferences("downloads", Context.MODE_PRIVATE),
            )
        }
        single { OfflineProgress(get(), get(), get()) }
        single<PendingProgress> { get<OfflineProgress>() }
        // Created at start: it picks up interrupted downloads and sends offline positions.
        single { CompactDownloader(get(), get(), get(named(APP_SCOPE))) }
        single { OriginalDownloader(get(), get()) }
        single<StorageSpace> { StatFsStorageSpace(get<DownloadFiles>().root) }
        single<DownloadForeground> { ServiceDownloadForeground(androidContext()) }
        single(createdAtStart = true) {
            DownloadStore(
                files = get(),
                repository = get(),
                compact = get(),
                original = get(),
                offlineProgress = get(),
                settings = get(),
                connectivity = get(),
                library = get(),
                storage = get(),
                clock = get(),
                scope = get(named(APP_SCOPE)),
                io = get<AppDispatchers>().io,
                foreground = get(),
            ).also { it.start() }
        }
        single<OfflineAvailability> { OfflineAvailability(get<DownloadStore>()::isOnDevice) }
        single<LocalDownloads> { LocalDownloads(get<DownloadStore>()::localFile) }
        single<OfflinePositions> { OfflinePositions(get<DownloadStore>()::recordOfflinePosition) }
        viewModelOf(::DownloadsViewModel)
        viewModel { params -> DownloadedSeriesViewModel(params.get(), get(), get()) }
    }
