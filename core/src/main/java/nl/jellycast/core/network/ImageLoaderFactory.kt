package nl.jellycast.core.network

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageResult
import coil3.serviceLoaderEnabled
import coil3.util.DebugLogger
import okhttp3.OkHttpClient

// The server sends WebP when the `Accept` header asks for it: smaller than JPEG at equal quality.
fun createImageLoader(
    context: Context,
    serverAddress: () -> String?,
    enableLogging: Boolean = false,
): ImageLoader {
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
        .components {
            add(ServerImageInterceptor(serverAddress))
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }
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

/**
 * A server image is cached by its path and fetched from the address in use, so a poster cached
 * at home shows over a VPN too, also from a library synced at home with the home address in it.
 */
private class ServerImageInterceptor(private val serverAddress: () -> String?) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val path = (chain.request.data as? String)?.let(::serverImagePath)
        val address = serverAddress()?.trimEnd('/')
        if (path == null || address == null) return chain.proceed()
        val request =
            chain.request
                .newBuilder()
                .data(address + path)
                .memoryCacheKey(path)
                .diskCacheKey(path)
                .build()
        return chain.withRequest(request).proceed()
    }
}

/** `/Items/<id>/Images/…?…` of a Jellyfin image URL, or `null` for any other URL. */
internal fun serverImagePath(url: String): String? {
    if (!url.startsWith("http")) return null
    val start = url.indexOf("/Items/")
    return url.substring(start).takeIf { start > 0 && "/Images/" in it }
}

private const val MEMORY_CACHE_PERCENT = 0.20
private const val DISK_CACHE_BYTES = 200L * 1024 * 1024
