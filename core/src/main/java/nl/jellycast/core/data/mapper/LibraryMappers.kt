package nl.jellycast.core.data.mapper

import java.time.ZoneOffset
import nl.jellycast.core.model.AgeRating
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.DynamicRange
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Resolution
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.Spine
import nl.jellycast.core.model.ticksToMs
import nl.jellycast.core.network.ImageKind
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.AudioSpatialFormat
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

// Requested widths in px: display size in dp × 2 on the Chromecast.
internal object ImageWidths {
    const val POSTER = 240
    const val LANDSCAPE = 392
    const val BACKDROP = 1280
    const val LOGO = 800
    const val SPINE_POSTER = 88
    const val SPINE_LOGO = 480
}

/** [passthroughAudio]: see `AudioPassthrough`; only Atmos in those codecs counts as Atmos. */
fun BaseItemDto.toMovie(images: ImageUrlBuilder, passthroughAudio: Set<String>): Movie {
    val itemId = id.toJellyfinId()
    val video = mediaStreams.orEmpty().firstOrNull { it.type == MediaStreamType.VIDEO }
    val spineColor =
        spineColor(
            posterBlurHash = blurHash(ImageType.PRIMARY),
            logoBlurHash = blurHash(ImageType.LOGO),
        )
    return Movie(
        id = itemId,
        title = name.orEmpty(),
        year = productionYear,
        runtimeMs = runTimeTicks?.ticksToMs(),
        overview = overview,
        genres = genres.orEmpty(),
        ageRating = officialRating.toAgeRating(),
        resumePositionMs = userData?.playbackPositionTicks?.ticksToMs() ?: 0,
        isPlayed = userData?.played == true,
        lastPlayedAt = userData?.lastPlayedDate?.toEpochMs(),
        addedAt = dateCreated?.toEpochMs(),
        artwork = artwork(images),
        spine =
            Spine(
                posterUrl = image(images, ImageType.PRIMARY, ImageWidths.SPINE_POSTER),
                logoUrl = image(images, ImageType.LOGO, ImageWidths.SPINE_LOGO),
                color = spineColor.argb,
                isLight = spineColor.isLight,
                darkColor = spineColor.darkArgb,
            ),
        tech =
            MediaTech(
                resolution = (video?.width ?: width).toResolution(),
                dynamicRange = video?.videoRangeType?.toDynamicRange(),
                hasAtmos = mediaStreams.orEmpty().any { it.isPlayableAtmos(passthroughAudio) },
            ),
        communityRating = communityRating,
        criticRating = criticRating,
        externalIds = externalIds(),
    )
}

fun BaseItemDto.toSeries(images: ImageUrlBuilder): Series = Series(
    id = id.toJellyfinId(),
    title = name.orEmpty(),
    year = productionYear,
    overview = overview,
    genres = genres.orEmpty(),
    ageRating = officialRating.toAgeRating(),
    seasonCount = childCount,
    lastEpisodeAddedAt = dateLastMediaAdded?.toEpochMs(),
    artwork = artwork(images),
    isPlayed = userData?.played == true,
    hasPlayedEpisodes = userData?.played == true || (userData?.playedPercentage ?: 0.0) > 0,
    communityRating = communityRating,
    externalIds = externalIds(),
)

fun BaseItemDto.toEpisode(images: ImageUrlBuilder): Episode {
    val seriesThumb =
        parentThumbItemId?.let { parent ->
            parentThumbImageTag?.let { tag ->
                images.build(parent.toJellyfinId(), ImageKind.Thumb, tag, ImageWidths.LANDSCAPE)
            }
        }
    val backdrops =
        parentBackdropItemId?.let { parent ->
            images.backdrops(parent.toJellyfinId(), parentBackdropImageTags)
        }.orEmpty()
    val logo =
        parentLogoItemId?.let { parent ->
            parentLogoImageTag?.let { tag ->
                images.build(parent.toJellyfinId(), ImageKind.Logo, tag, ImageWidths.LOGO)
            }
        }
    return Episode(
        id = id.toJellyfinId(),
        seriesId = seriesId?.toJellyfinId().orEmpty(),
        seriesTitle = seriesName.orEmpty(),
        seasonId = seasonId?.toJellyfinId(),
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        title = name.orEmpty(),
        overview = overview,
        runtimeMs = runTimeTicks?.ticksToMs(),
        resumePositionMs = userData?.playbackPositionTicks?.ticksToMs() ?: 0,
        artwork =
            Artwork(
                landscape =
                    seriesThumb ?: image(images, ImageType.PRIMARY, ImageWidths.LANDSCAPE),
                backdrop = backdrops.firstOrNull(),
                logo = logo,
                backdrops = backdrops,
            ),
        isPlayed = userData?.played == true,
        lastPlayedAt = userData?.lastPlayedDate?.toEpochMs(),
        addedAt = dateCreated?.toEpochMs(),
        stillUrl = image(images, ImageType.PRIMARY, ImageWidths.LANDSCAPE),
        communityRating = communityRating,
        externalIds = externalIds(),
    )
}

private fun BaseItemDto.artwork(images: ImageUrlBuilder): Artwork {
    val backdrops = images.backdrops(id.toJellyfinId(), backdropImageTags)
    return Artwork(
        poster = image(images, ImageType.PRIMARY, ImageWidths.POSTER),
        landscape = image(images, ImageType.THUMB, ImageWidths.LANDSCAPE),
        backdrop = backdrops.firstOrNull(),
        logo = image(images, ImageType.LOGO, ImageWidths.LOGO),
        backdrops = backdrops,
    )
}

private fun ImageUrlBuilder.backdrops(itemId: String, tags: List<String>?): List<String> =
    tags.orEmpty().map { tag -> build(itemId, ImageKind.Backdrop, tag, ImageWidths.BACKDROP) }

private fun BaseItemDto.image(images: ImageUrlBuilder, type: ImageType, width: Int): String? {
    val tag = imageTags?.get(type) ?: return null
    val kind =
        when (type) {
            ImageType.LOGO -> ImageKind.Logo
            ImageType.THUMB -> ImageKind.Thumb
            ImageType.BACKDROP -> ImageKind.Backdrop
            else -> ImageKind.Primary
        }
    return images.build(id.toJellyfinId(), kind, tag, width)
}

private fun BaseItemDto.blurHash(type: ImageType): String? {
    val tag = imageTags?.get(type) ?: return null
    return imageBlurHashes?.get(type)?.get(tag)
}

fun java.util.UUID.toJellyfinId(): String = toString().replace("-", "")

private fun DateTime.toEpochMs(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

/** "NL-12" → "12"; other ratings as the server gives them ("R", "TV-MA"). */
internal fun String?.toAgeRating(): AgeRating? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return AgeRating(value.removePrefix("NL-"))
}

private const val UHD_MIN_WIDTH = 3200
private const val HD_MIN_WIDTH = 1200

internal fun Int?.toResolution(): Resolution? = when {
    this == null || this <= 0 -> null
    this >= UHD_MIN_WIDTH -> Resolution.UHD
    this >= HD_MIN_WIDTH -> Resolution.HD
    else -> Resolution.SD
}

internal fun VideoRangeType.toDynamicRange(): DynamicRange? = when (this) {
    VideoRangeType.DOVI,
    VideoRangeType.DOVI_WITH_HDR10,
    VideoRangeType.DOVI_WITH_HLG,
    VideoRangeType.DOVI_WITH_SDR,
    VideoRangeType.DOVI_WITH_EL,
    VideoRangeType.DOVI_WITH_HDR10_PLUS,
    VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
    // Profile 8.6 plays through the DV decoder.
    VideoRangeType.DOVI_INVALID,
    -> DynamicRange.DolbyVision

    VideoRangeType.HDR10_PLUS -> DynamicRange.Hdr10Plus

    VideoRangeType.HDR10 -> DynamicRange.Hdr10

    VideoRangeType.HLG -> DynamicRange.Hlg

    VideoRangeType.SDR, VideoRangeType.UNKNOWN -> null
}

// Atmos is lost when the device decodes the track or the server transcodes it, so the label
// would promise something that never reaches the receiver.
private fun MediaStream.isPlayableAtmos(passthroughAudio: Set<String>): Boolean =
    type == MediaStreamType.AUDIO &&
        audioSpatialFormat == AudioSpatialFormat.DOLBY_ATMOS &&
        codec?.lowercase() in passthroughAudio

internal fun BaseItemDto.externalIds(): ExternalIds = ExternalIds(
    imdb = providerIds?.get("Imdb")?.takeIf { it.isNotBlank() },
    tmdb = providerIds?.get("Tmdb")?.takeIf { it.isNotBlank() },
    tmdbCollection = providerIds?.get("TmdbCollection")?.takeIf { it.isNotBlank() },
)
