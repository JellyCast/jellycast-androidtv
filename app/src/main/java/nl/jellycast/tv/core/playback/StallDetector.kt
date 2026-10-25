package nl.jellycast.tv.core.playback

/** Free of Media3 types, so [StallDetector] can be tested on the JVM. */
data class PlayerSnapshot(
    val playWhenReady: Boolean,
    val buffering: Boolean,
    val playing: Boolean,
    val positionMs: Long,
    val bufferedPositionMs: Long,
)

enum class StallKind {
    /** Buffering with plenty of data ahead: the decoder or audio output is stuck. */
    BufferingWithData,

    /** "Playing" while the position (the audio clock) stands still: audio output is stuck. */
    ClockStopped,

    /**
     * Keeps buffering with (almost) nothing ahead after playback got going, or never gets going:
     * the network cannot keep up.
     */
    TooSlow,
}

sealed interface StallAction {
    data object None : StallAction

    /** Seeking to the same position makes Media3 rebuild decoder and audio output. */
    data class Reseek(val kind: StallKind) : StallAction

    /** Came back within [StallDetector.ESCALATE_WITHIN_MS]: hand over to the fallback. */
    data class Escalate(val kind: StallKind) : StallAction
}

/**
 * Detects a player that hangs without an error, which Media3 does not report (e.g. media#3407: a
 * passthrough position running backwards keeps the player buffering). Call [check] about every
 * second. Normal buffering never has 10 s ahead: Media3 already plays at 2.5–5 s.
 */
class StallDetector {
    private var bufferingSinceMs: Long? = null
    private var clockStillSinceMs: Long? = null
    private var lastPositionMs = Long.MIN_VALUE
    private var lastReseekAtMs: Long? = null
    private var starvedSinceMs: Long? = null
    private var playedSinceSeek = false
    private var startingSinceMs: Long? = null

    /** Finished starved buffering episodes as (end, duration). */
    private val starvedEpisodes = ArrayDeque<Pair<Long, Long>>()

    fun check(nowMs: Long, snapshot: PlayerSnapshot): StallAction {
        val aheadMs = snapshot.bufferedPositionMs - snapshot.positionMs
        if (tooSlow(nowMs, snapshot, aheadMs)) {
            starvedSinceMs = null
            starvedEpisodes.clear()
            return StallAction.Escalate(StallKind.TooSlow)
        }
        bufferingSinceMs =
            if (snapshot.playWhenReady && snapshot.buffering && aheadMs >= MIN_AHEAD_MS) {
                bufferingSinceMs ?: nowMs
            } else {
                null
            }
        clockStillSinceMs =
            if (snapshot.playing && snapshot.positionMs == lastPositionMs) {
                clockStillSinceMs ?: nowMs
            } else {
                null
            }
        lastPositionMs = snapshot.positionMs

        val kind =
            when {
                bufferingSinceMs?.let { nowMs - it >= BUFFERING_LIMIT_MS } == true ->
                    StallKind.BufferingWithData

                clockStillSinceMs?.let { nowMs - it >= CLOCK_LIMIT_MS } == true ->
                    StallKind.ClockStopped

                else -> return StallAction.None
            }
        bufferingSinceMs = null
        clockStillSinceMs = null
        val previous = lastReseekAtMs
        return if (previous != null && nowMs - previous < ESCALATE_WITHIN_MS) {
            lastReseekAtMs = null
            StallAction.Escalate(kind)
        } else {
            lastReseekAtMs = nowMs
            StallAction.Reseek(kind)
        }
    }

    /**
     * Call on every seek, including the reseeks this detector asks for. Loading after a start or a
     * seek fills the buffer from zero, so it says nothing about whether the network keeps up.
     */
    fun onSeek() {
        playedSinceSeek = false
        starvedSinceMs = null
        startingSinceMs = null
    }

    /** Reseeking does not help a slow network, so this goes straight to the fallback. */
    private fun tooSlow(nowMs: Long, snapshot: PlayerSnapshot, aheadMs: Long): Boolean {
        val starved = snapshot.playWhenReady && snapshot.buffering && aheadMs < MIN_AHEAD_MS
        if (snapshot.playing) {
            playedSinceSeek = true
            startingSinceMs = null
        }
        if (!playedSinceSeek) return startingTooLong(nowMs, starved)
        val since = starvedSinceMs
        if (starved && since == null) {
            starvedSinceMs = nowMs
        } else if (!starved && since != null) {
            starvedEpisodes.addLast(nowMs to nowMs - since)
            starvedSinceMs = null
        }
        while (starvedEpisodes.firstOrNull()?.let { nowMs - it.first > STARVED_WINDOW_MS } ==
            true
        ) {
            starvedEpisodes.removeFirst()
        }
        val current = starvedSinceMs?.let { nowMs - it } ?: 0
        return current >= STARVED_LIMIT_MS ||
            current + starvedEpisodes.sumOf { it.second } >= STARVED_TOTAL_MS
    }

    private fun startingTooLong(nowMs: Long, starved: Boolean): Boolean {
        if (!starved) {
            startingSinceMs = null
            return false
        }
        val since = startingSinceMs ?: nowMs.also { startingSinceMs = it }
        return nowMs - since >= STARTING_LIMIT_MS
    }

    companion object {
        const val STARTING_LIMIT_MS = 30_000L
        const val STARVED_LIMIT_MS = 10_000L
        const val STARVED_TOTAL_MS = 20_000L
        const val STARVED_WINDOW_MS = 120_000L
        const val MIN_AHEAD_MS = 10_000L
        const val BUFFERING_LIMIT_MS = 4_000L
        const val CLOCK_LIMIT_MS = 3_000L
        const val ESCALATE_WITHIN_MS = 60_000L
    }
}

fun StallKind.toFailure(): PlaybackFailure = when (this) {
    StallKind.BufferingWithData -> PlaybackFailure.Video
    StallKind.ClockStopped -> PlaybackFailure.Audio
    StallKind.TooSlow -> PlaybackFailure.TooSlow
}
