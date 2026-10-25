package nl.jellycast.tv.core.network

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import coil3.util.DebugLogger
import okhttp3.OkHttpClient

// The server sends WebP when the `Accept` header asks for it: smaller than JPEG at equal quality.
fun createImageLoader(context: Context, enableLogging: Boolean = false): ImageLoader {
    val client =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("Accept", "image/webp,image/*").build(),
                )
            }.build()
    return ImageLoader
        .Builder(context)
        // ServiceLoader lookup slows the cold start; the only component needed is added below.
        .serviceLoaderEnabled(false)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build()
        }.diskCache {
            DiskCache
                .Builder()
                .directory(context.cacheDir.resolve("images"))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }.apply {
            if (enableLogging) logger(DebugLogger())
        }.build()
}

private const val MEMORY_CACHE_PERCENT = 0.20
private const val DISK_CACHE_BYTES = 200L * 1024 * 1024
