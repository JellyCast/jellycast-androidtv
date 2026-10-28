package nl.jellycast.tv

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import nl.jellycast.core.data.repository.LanguageRepository
import nl.jellycast.tv.di.jellyCastModules
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class JellyCastApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Release builds log nothing, so tokens and stream URLs never reach logcat.
        if (BuildConfig.DEV_TOOLS) Timber.plant(Timber.DebugTree())

        startKoin {
            androidContext(this@JellyCastApp)
            modules(jellyCastModules)
        }
        // Before the first Activity, so it opens in the default language instead of the TV's.
        get<LanguageRepository>().applyDefaultIfUnset()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = get()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            get<ImageLoader>().memoryCache?.clear()
        }
    }
}
