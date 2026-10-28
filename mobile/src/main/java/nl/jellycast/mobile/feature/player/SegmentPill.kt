package nl.jellycast.mobile.feature.player

import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.SegmentType
import nl.jellycast.core.model.isFinishedAt
import nl.jellycast.mobile.core.settings.QualityLevel

/** The one pill at the bottom end of the video. */
sealed interface SegmentPill {
    /** [lowerLevel] is named in the hint when a lower level plays smoother. */
    data class SlowConnection(val lowerLevel: QualityLevel?) : SegmentPill

    data class Skip(val type: SegmentType, val endMs: Long) : SegmentPill

    data class Next(val item: PlayableItem, val fromList: Boolean) : SegmentPill
}

private val SkippableTypes = setOf(SegmentType.Intro, SegmentType.Recap, SegmentType.Preview)

// In the last second of a segment skipping gains nothing.
private const val SKIP_TAIL_MS = 1_000L

/** In order: the slow-connection hint, a skip pill, the next pill in the credits. */
fun segmentPill(
    positionMs: Long,
    durationMs: Long,
    segments: List<MediaSegment>,
    nextItem: PlayableItem?,
    fromList: Boolean,
    slowHint: SegmentPill.SlowConnection?,
): SegmentPill? {
    if (slowHint != null) return slowHint
    segments
        .firstOrNull {
            it.type in SkippableTypes &&
                positionMs >= it.startMs &&
                positionMs < it.endMs - SKIP_TAIL_MS
        }?.let { return SegmentPill.Skip(it.type, it.endMs) }
    if (nextItem != null && segments.isFinishedAt(positionMs, durationMs)) {
        return SegmentPill.Next(nextItem, fromList)
    }
    return null
}
