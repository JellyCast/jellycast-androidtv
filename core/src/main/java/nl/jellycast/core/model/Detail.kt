package nl.jellycast.core.model

import java.time.LocalDate

enum class CreditKind { Actor, GuestStar, Director, Writer, Producer }

/** Someone credited on a movie, series or episode, in the server's (billing) order. */
data class Credit(
    val personId: String,
    val name: String,
    val role: String?,
    val kind: CreditKind,
    val imageUrl: String?,
)

/** What the server knows about a person beyond the credit, mostly from TMDb and Wikipedia. */
data class PersonDetails(
    val birthDate: LocalDate? = null,
    /** "Manhattan, USA": the first and last part of the birthplace, the country in English. */
    val birthplace: Birthplace? = null,
    val biography: String? = null,
    val externalIds: ExternalIds = ExternalIds(),
)

data class Birthplace(val place: String, val country: String?)

enum class StreamKind { Video, Audio, Subtitle, Other }

data class MediaStreamInfo(
    val kind: StreamKind,
    val codec: String? = null,
    val profile: String? = null,
    val height: Int? = null,
    val videoRangeType: String? = null,
    val channels: Int? = null,
    val isDefault: Boolean = false,
)

/** The streams of the item's first media source. */
data class MediaInfo(val streams: List<MediaStreamInfo>)

/** One request's worth of detail: who made it, its studios and its file. */
data class ItemDetail(
    val credits: List<Credit>,
    val peopleIds: Set<String>,
    val studioIds: Set<String>,
    val media: MediaInfo?,
)

/** A movie the server calls similar, with what it shares to rank it on. */
data class SimilarMovie(val id: String, val peopleIds: Set<String>, val studioIds: Set<String>)
