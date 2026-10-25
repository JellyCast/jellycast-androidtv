package nl.jellycast.tv.core.data.repository

import nl.jellycast.tv.core.model.MediaSegment
import nl.jellycast.tv.core.model.SegmentType

/**
 * Dev builds only: fake segments to test skip and next-episode on the device, set by
 * `MainActivity` from `--es debugSegments "intro:5-60,outro:1650-1721"` (seconds).
 */
object DebugSegments {
    @Volatile var override: List<MediaSegment>? = null
}

/** Unreadable parts are dropped. */
fun parseDebugSegments(text: String): List<MediaSegment> = text.split(',').mapNotNull { part ->
    val (name, range) = part.trim().split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
    val type = SegmentType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    val (start, end) = range.split('-').mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 }
        ?: return@mapNotNull null
    type?.let { MediaSegment(it, start * 1000, end * 1000) }
}
