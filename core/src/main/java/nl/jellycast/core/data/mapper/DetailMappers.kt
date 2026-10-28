package nl.jellycast.core.data.mapper

import java.time.LocalDateTime
import nl.jellycast.core.model.Birthplace
import nl.jellycast.core.model.Credit
import nl.jellycast.core.model.CreditKind
import nl.jellycast.core.model.ItemDetail
import nl.jellycast.core.model.MediaInfo
import nl.jellycast.core.model.MediaStreamInfo
import nl.jellycast.core.model.PersonDetails
import nl.jellycast.core.model.SimilarMovie
import nl.jellycast.core.model.StreamKind
import nl.jellycast.core.network.ImageKind
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind

fun BaseItemDto.toItemDetail(images: ImageUrlBuilder): ItemDetail = ItemDetail(
    credits = people.orEmpty().mapNotNull { it.toCredit(images) },
    peopleIds = peopleIds(),
    studioIds = studioIds(),
    media =
        mediaSources?.firstOrNull()?.let { source ->
            MediaInfo(source.mediaStreams.orEmpty().map { it.toStreamInfo() })
        },
)

fun BaseItemDto.toSimilarMovie(): SimilarMovie =
    SimilarMovie(id.toJellyfinId(), peopleIds(), studioIds())

fun BaseItemDto.toPersonDetails(): PersonDetails = PersonDetails(
    birthDate = premiereDate?.let(::calendarDate),
    birthplace = productionLocations?.firstOrNull()?.let(::birthplace),
    biography = overview?.let(::cleanBiography),
    externalIds = externalIds(),
)

private fun BaseItemDto.peopleIds(): Set<String> =
    people.orEmpty().mapTo(mutableSetOf()) { it.id.toJellyfinId() }

private fun BaseItemDto.studioIds(): Set<String> =
    studios.orEmpty().mapTo(mutableSetOf()) { it.id.toJellyfinId() }

private fun BaseItemPerson.toCredit(images: ImageUrlBuilder): Credit? {
    val kind =
        when (type) {
            PersonKind.ACTOR -> CreditKind.Actor
            PersonKind.GUEST_STAR -> CreditKind.GuestStar
            PersonKind.DIRECTOR -> CreditKind.Director
            PersonKind.WRITER -> CreditKind.Writer
            PersonKind.PRODUCER -> CreditKind.Producer
            else -> return null
        }
    val personId = id.toJellyfinId()
    return Credit(
        personId = personId,
        name = name.orEmpty(),
        role = role?.takeIf { it.isNotBlank() },
        kind = kind,
        imageUrl = primaryImageTag?.let {
            images.build(personId, ImageKind.Primary, it, PERSON_IMAGE_WIDTH)
        },
    )
}

private fun MediaStream.toStreamInfo(): MediaStreamInfo = MediaStreamInfo(
    kind =
        when (type) {
            MediaStreamType.VIDEO -> StreamKind.Video
            MediaStreamType.AUDIO -> StreamKind.Audio
            MediaStreamType.SUBTITLE -> StreamKind.Subtitle
            else -> StreamKind.Other
        },
    codec = codec,
    profile = profile,
    height = height,
    videoRangeType = videoRangeType.serialName,
    channels = channels,
    isDefault = isDefault,
)

// The server stores a birthday as midnight in its own time zone, sent in UTC (the evening before
// for Europe). Noon lands on the right calendar day for any zone within 12 hours.
internal fun calendarDate(utc: LocalDateTime) = utc.plusHours(HALF_DAY_HOURS).toLocalDate()

/** "Manhattan, New York City, New York, USA" becomes Manhattan in USA. */
internal fun birthplace(raw: String): Birthplace? {
    val parts = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val first = parts.firstOrNull() ?: return null
    return Birthplace(first, parts.takeIf { it.size > 1 }?.last())
}

private val WikipediaMarkers =
    listOf("Description above from the Wikipedia", "Description above from Wikipedia")

// TMDb copies Wikipedia's attribution into the text; that isn't part of the biography.
internal fun cleanBiography(raw: String): String? {
    var text = raw
    for (marker in WikipediaMarkers) {
        val index = text.indexOf(marker)
        if (index >= 0) text = text.substring(0, index)
    }
    return text.replace("From Wikipedia, the free encyclopedia.", "").trim().ifEmpty { null }
}

private const val HALF_DAY_HOURS = 12L

// 64 dp photo at up to 3x.
private const val PERSON_IMAGE_WIDTH = 192
