package nl.jellycast.mobile.ui.downloads

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.ceil
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.downloads.Download
import nl.jellycast.mobile.core.downloads.DownloadKind
import nl.jellycast.mobile.core.downloads.DownloadStatus
import nl.jellycast.mobile.core.downloads.DownloadedMedia

/**
 * The words for a download's size and progress, shared by the button, the list, the bar and
 * the notification (which has no composition, hence a [Context]).
 */
class DownloadText(private val context: Context) {
    private val isTablet: Boolean
        get() = context.resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP

    fun bytes(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    /** "± 5.7 GB" before the size is known. */
    fun estimate(bytes: Long): String = "± " + bytes(bytes)

    fun kind(kind: DownloadKind): String = context.getString(
        if (kind ==
            DownloadKind.Compact
        ) {
            R.string.downloads_kind_compact
        } else {
            R.string.downloads_kind_original
        },
    )

    fun onThisDevice(): String = context.getString(
        if (isTablet) R.string.downloads_on_this_tablet else R.string.downloads_on_this_phone,
    )

    fun freeOnThisDevice(free: Long): String = context.getString(
        if (isTablet) R.string.downloads_free_on_tablet else R.string.downloads_free_on_phone,
        bytes(free),
    )

    /** "The Pitt · S1 · E3" for an episode, the title for a movie. */
    fun title(media: DownloadedMedia): String {
        if (!media.isEpisode) return media.title
        return joined(media.seriesTitle, episodeCode(media))
    }

    fun episodeCode(media: DownloadedMedia): String? {
        val season = media.seasonNumber
        val number = media.episodeNumber
        return when {
            season != null && number != null -> context.getString(
                R.string.episode_code,
                season,
                number,
            )

            number != null -> context.getString(R.string.episode_number, number)

            else -> null
        }
    }

    /** "Compact · 1080p · ± 1.1 GB", or the final size once done. */
    fun sizeLine(download: Download): String {
        val size =
            if (download.status == DownloadStatus.Done) {
                bytes(download.bytes)
            } else {
                download.totalBytes?.let(::bytes) ?: download.estimatedBytes?.let(::estimate)
            }
        return joined(kind(download.kind), size)
    }

    /** "Converting on the server · 42% · about 4 min left", "Waiting in line", … */
    fun statusLine(download: Download, secondsLeft: Double?): String {
        val percent = download.fraction?.let { "${(it * PERCENT).toInt()}%" }
        return when (val status = download.status) {
            DownloadStatus.Queued -> context.getString(R.string.downloads_status_queued)

            DownloadStatus.Running -> {
                val action =
                    if (download.kind == DownloadKind.Compact) {
                        R.string.downloads_status_converting
                    } else {
                        R.string.downloads_status_downloading
                    }
                joined(context.getString(action), percent, secondsLeft?.let(::timeLeft))
            }

            DownloadStatus.WaitingForNetwork ->
                context.getString(R.string.downloads_status_waiting_network)

            DownloadStatus.Paused -> joined(
                context.getString(R.string.downloads_status_paused),
                percent,
            )

            is DownloadStatus.Failed -> context.getString(
                R.string.downloads_status_failed,
                status.message,
            )

            DownloadStatus.Done -> onThisDevice()
        }
    }

    fun timeLeft(seconds: Double): String {
        val minutes = ceil(seconds / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)
        return context.getString(R.string.downloads_minutes_left, minutes)
    }

    fun doesNotFit(missingBytes: Long): String =
        context.getString(R.string.downloads_does_not_fit, bytes(missingBytes))

    fun doesNotFitShort(missingBytes: Long): String =
        context.getString(R.string.downloads_does_not_fit_short, bytes(missingBytes))

    fun onlyFit(queued: Int, of: Int): String =
        context.getString(R.string.downloads_only_fit, queued, of)

    companion object {
        private const val TABLET_MIN_WIDTH_DP = 600
        private const val PERCENT = 100
        private const val SECONDS_PER_MINUTE = 60.0

        fun joined(vararg parts: String?): String =
            parts.filterNot { it.isNullOrEmpty() }.joinToString(" · ")
    }
}

@Composable
fun rememberDownloadText(): DownloadText {
    val context = LocalContext.current
    return remember(context) { DownloadText(context) }
}
