package nl.jellycast.tv.core.playback

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import nl.jellycast.tv.core.common.Clock
import timber.log.Timber

const val START_LOG_KEEP = 10

private const val HEADER = "=== "

/**
 * What happened during the last [START_LOG_KEEP] starts, readable via adb even in a release build:
 * an intermittent start error is often reported after logcat has rolled over. One ordered queue on
 * the app scope, so playback never waits for the disk. Never write URLs or tokens here.
 */
class PlaybackStartLog(
    private val file: File,
    appScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) {
    private val queue = Channel<() -> Unit>(Channel.UNLIMITED)
    private var startedAt = 0L

    init {
        appScope.launch(ioDispatcher) {
            for (write in queue) {
                try {
                    write()
                } catch (e: IOException) {
                    Timber.w(e, "Writing the start log failed")
                }
            }
        }
    }

    fun begin(description: String) {
        startedAt = clock.nowMs()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(startedAt))
        val header = "$HEADER$date $description\n"
        queue.trySend {
            val old = if (file.exists()) file.readText() else ""
            file.parentFile?.mkdirs()
            file.writeText(keepLastStarts(old, START_LOG_KEEP - 1) + header)
        }
    }

    /** Prefixed with the ms since [begin]. */
    fun log(message: String) {
        val line = "+${clock.nowMs() - startedAt} ms  $message\n"
        queue.trySend { file.appendText(line) }
    }
}

internal fun keepLastStarts(text: String, keep: Int): String {
    val lines = text.lines().filter { it.isNotEmpty() }
    val headers = lines.indices.filter { lines[it].startsWith(HEADER) }
    if (keep <= 0 || headers.isEmpty()) return ""
    val from = headers.getOrNull(headers.size - keep) ?: headers.first()
    return lines.drop(from).joinToString("") { "$it\n" }
}
