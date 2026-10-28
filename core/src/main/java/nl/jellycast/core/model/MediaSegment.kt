package nl.jellycast.core.model

enum class SegmentType { Intro, Recap, Commercial, Preview, Outro }

data class MediaSegment(val type: SegmentType, val startMs: Long, val endMs: Long)

// In the last second of a segment skipping gains nothing, so no button.
private const val SKIP_TAIL_MS = 1_000L

const val NEXT_CARD_BEFORE_END_MS = 30_000L

/** The outro is never skippable: it shows the next-episode card instead ([nextCardStartMs]). */
fun List<MediaSegment>.skippableAt(positionMs: Long): MediaSegment? = firstOrNull {
    it.type != SegmentType.Outro &&
        positionMs >= it.startMs &&
        positionMs < it.endMs - SKIP_TAIL_MS
}

// An outro in the first half is a detection error and is ignored.
fun List<MediaSegment>.nextCardStartMs(durationMs: Long): Long {
    val outro =
        firstOrNull {
            it.type == SegmentType.Outro && it.startMs >= durationMs / 2 && it.startMs < durationMs
        }
    return outro?.startMs ?: (durationMs - NEXT_CARD_BEFORE_END_MS)
}

/**
 * Stopping in the credits counts as watched; the server only does that above 90%, and credits
 * often start earlier. An unknown duration (≤ 0) never counts.
 */
fun List<MediaSegment>.isFinishedAt(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= nextCardStartMs(durationMs)
