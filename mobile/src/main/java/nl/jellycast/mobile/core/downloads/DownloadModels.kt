package nl.jellycast.mobile.core.downloads

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Spine

@Serializable
enum class DownloadKind {
    /** The server's 1080p HEVC transcode, appended into one fragmented MP4. */
    Compact,

    /** The file as it is on the server. */
    Original,
}

@Serializable
sealed interface DownloadStatus {
    @Serializable
    @SerialName("queued")
    data object Queued : DownloadStatus

    @Serializable
    @SerialName("running")
    data object Running : DownloadStatus

    @Serializable
    @SerialName("waitingForNetwork")
    data object WaitingForNetwork : DownloadStatus

    @Serializable
    @SerialName("paused")
    data object Paused : DownloadStatus

    @Serializable
    @SerialName("done")
    data object Done : DownloadStatus

    @Serializable
    @SerialName("failed")
    data class Failed(val message: String) : DownloadStatus

    val isActive: Boolean get() = this == Queued || this == Running || this == WaitingForNetwork
}

/**
 * What the app knows about a downloaded movie or episode, kept with the download so it can be
 * listed and played without the server.
 */
@Serializable
data class DownloadedMedia(
    val id: String,
    val title: String,
    val runtimeMs: Long? = null,
    val posterUrl: String? = null,
    val landscapeUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val year: Int? = null,
    /** Set for an episode. */
    val seriesId: String? = null,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val stillUrl: String? = null,
    val seriesPosterUrl: String? = null,
) {
    val isEpisode: Boolean get() = seriesId != null

    fun toPlayable(): PlayableItem = if (seriesId != null) {
        PlayableItem.EpisodeItem(
            Episode(
                id = id,
                seriesId = seriesId,
                seriesTitle = seriesTitle.orEmpty(),
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                title = title,
                overview = null,
                runtimeMs = runtimeMs,
                resumePositionMs = 0,
                artwork =
                    Artwork(
                        poster = seriesPosterUrl,
                        landscape = landscapeUrl,
                        backdrop = backdropUrl,
                        logo = logoUrl,
                    ),
                stillUrl = stillUrl,
            ),
        )
    } else {
        PlayableItem.MovieItem(
            Movie(
                id = id,
                title = title,
                year = year,
                runtimeMs = runtimeMs,
                overview = null,
                genres = emptyList(),
                ageRating = null,
                resumePositionMs = 0,
                lastPlayedAt = null,
                addedAt = null,
                artwork =
                    Artwork(
                        poster = posterUrl,
                        landscape = landscapeUrl,
                        backdrop = backdropUrl,
                        logo = logoUrl,
                    ),
                spine = Spine(posterUrl = posterUrl, logoUrl = logoUrl, color = 0, isLight = false),
                tech = MediaTech(),
            ),
        )
    }

    companion object {
        fun of(item: PlayableItem, seriesPosterUrl: String? = null): DownloadedMedia = when (item) {
            is PlayableItem.MovieItem -> with(item.movie) {
                DownloadedMedia(
                    id = id,
                    title = title,
                    runtimeMs = runtimeMs,
                    posterUrl = artwork.poster,
                    landscapeUrl = artwork.landscape,
                    backdropUrl = artwork.backdrop,
                    logoUrl = artwork.logo,
                    year = year,
                )
            }

            is PlayableItem.EpisodeItem -> with(item.episode) {
                DownloadedMedia(
                    id = id,
                    title = title,
                    runtimeMs = runtimeMs,
                    landscapeUrl = artwork.landscape,
                    backdropUrl = artwork.backdrop,
                    logoUrl = artwork.logo,
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    stillUrl = stillUrl,
                    seriesPosterUrl = seriesPosterUrl ?: artwork.poster,
                )
            }
        }
    }
}

/** One movie or episode on the device, or on its way there. */
@Serializable
data class Download(
    val media: DownloadedMedia,
    val kind: DownloadKind,
    val status: DownloadStatus = DownloadStatus.Queued,
    val bytes: Long = 0,
    /** The size when known: the original's size, or the Compact file once it is done. */
    val totalBytes: Long? = null,
    /** The expected size before it is known, for the free-space check. */
    val estimatedBytes: Long? = null,
    val mediaSeconds: Double = 0.0,
    val totalMediaSeconds: Double? = null,
    val addedAtMs: Long = 0,
) {
    val id: String get() = media.id

    val fraction: Float?
        get() {
            if (status == DownloadStatus.Done) return 1f
            val totalSeconds = totalMediaSeconds
            if (kind == DownloadKind.Compact && totalSeconds != null && totalSeconds > 0) {
                return (mediaSeconds / totalSeconds).toFloat().coerceAtMost(1f)
            }
            val total = totalBytes
            if (total != null && total > 0) return (bytes.toFloat() / total).coerceAtMost(1f)
            return null
        }

    /** What it takes on disk now, or will take once done. */
    val expectedBytes: Long
        get() = if (status == DownloadStatus.Done) {
            bytes
        } else {
            maxOf(totalBytes ?: estimatedBytes ?: 0, bytes)
        }
}

/** The choice offered for one item: Compact unless the original is no bigger. */
data class DownloadPlan(val compactBytes: Long?, val originalBytes: Long?) {
    val offersCompact: Boolean
        get() {
            val compact = compactBytes ?: return false
            val original = originalBytes ?: return true
            return original > compact
        }

    val defaultKind: DownloadKind
        get() = if (offersCompact) DownloadKind.Compact else DownloadKind.Original

    fun bytes(kind: DownloadKind): Long? =
        if (kind == DownloadKind.Compact) compactBytes else originalBytes

    companion object {
        /** Measured on the server's 1080p transcode (5 Mbit/s cap): ~4.9 Mbit/s with stereo AAC. */
        const val COMPACT_BITS_PER_SECOND = 4_900_000.0

        fun of(runtimeMs: Long?, originalBytes: Long?): DownloadPlan {
            val seconds = (runtimeMs ?: 0) / MS_PER_SECOND
            val compact =
                if (seconds >
                    0
                ) {
                    (seconds * COMPACT_BITS_PER_SECOND / BITS_PER_BYTE).toLong()
                } else {
                    null
                }
            return DownloadPlan(compact, originalBytes)
        }
    }
}

/** A plan with what the server said about the file (tech line, size). */
data class PlanResult(val plan: DownloadPlan, val techParts: List<String>)

/** How fast the running download goes, for the time left. */
data class DownloadRate(
    val itemId: String,
    val bytesPerSecond: Double,
    val mediaSecondsPerSecond: Double,
)

data class VolumeSpace(val totalBytes: Long, val freeBytes: Long)

val PlayableItem.runtimeMs: Long?
    get() = when (this) {
        is PlayableItem.MovieItem -> movie.runtimeMs
        is PlayableItem.EpisodeItem -> episode.runtimeMs
    }

/** Season, then episode; movies keep their order. */
val EpisodeOrder: Comparator<DownloadedMedia> =
    compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 })

private const val MS_PER_SECOND = 1000.0
private const val BITS_PER_BYTE = 8
