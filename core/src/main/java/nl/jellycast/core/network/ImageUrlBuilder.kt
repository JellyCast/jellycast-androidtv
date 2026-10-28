package nl.jellycast.core.network

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUID

enum class ImageKind { Primary, Backdrop, Logo, Thumb }

/**
 * Always includes `tag`, which changes when the image changes, so URLs are safe to cache. An
 * interface so mappers can be tested without the SDK.
 */
fun interface ImageUrlBuilder {
    fun build(itemId: String, kind: ImageKind, tag: String, fillWidth: Int): String
}

class JellyfinImageUrlBuilder(private val api: ApiClient) : ImageUrlBuilder {
    override fun build(itemId: String, kind: ImageKind, tag: String, fillWidth: Int): String =
        api.imageApi.getItemImageUrl(
            itemId = itemId.toUUID(),
            imageType = kind.toImageType(),
            fillWidth = fillWidth,
            quality = IMAGE_QUALITY,
            tag = tag,
        )
}

private fun ImageKind.toImageType(): ImageType = when (this) {
    ImageKind.Primary -> ImageType.PRIMARY
    ImageKind.Backdrop -> ImageType.BACKDROP
    ImageKind.Logo -> ImageType.LOGO
    ImageKind.Thumb -> ImageType.THUMB
}

// Jellyfin defaults to 100; 85 looks the same at 3 metres and is much smaller.
private const val IMAGE_QUALITY = 85

private val FillWidthParam = Regex("""([?&]fillWidth=)\d+""")

/**
 * [url] as [ImageUrlBuilder] built it, asking for [fillWidth] pixels instead; lets a screen fetch
 * the size it draws without the models carrying image tags. A URL without the parameter stays.
 */
fun withFillWidth(url: String, fillWidth: Int): String =
    FillWidthParam.replace(url) { "${it.groupValues[1]}$fillWidth" }
