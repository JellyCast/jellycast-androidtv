package nl.jellycast.mobile

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import nl.jellycast.mobile.di.jellyCastModules
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class JellyCastMobileApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Release builds log nothing, so tokens and stream URLs never reach logcat.
        if (BuildConfig.DEV_TOOLS) Timber.plant(Timber.DebugTree())

        startKoin {
            androidContext(this@JellyCastMobileApp)
            modules(jellyCastModules)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = get()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            get<ImageLoader>().memoryCache?.clear()
        }
    }
}
