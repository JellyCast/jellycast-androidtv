package nl.jellycast.tv.core.playback

import kotlin.math.abs

/** Android's `Display.Mode`, without Android types so it can be tested on the JVM. */
data class DisplayModeSpec(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    /** Rates this mode can switch to seamlessly, without an HDMI resync. */
    val alternativeRefreshRates: List<Float> = emptyList(),
)

data class DisplayModeChoice(val mode: DisplayModeSpec, val seamless: Boolean)

/** Small enough to tell 23.976 Hz from 24 Hz. */
private const val REFRESH_TOLERANCE = 0.01f

private const val MIN_WIDTH = 1280
private const val MIN_HEIGHT = 720
private const val SAME_RATE = 1
private const val DOUBLE_RATE = 2

/**
 * Only an exact or double rate (25 → 50 Hz), never "close": 24p on 60 Hz just trades one judder
 * for another. Prefers the current resolution; another resolution is never smaller than the video
 * or 720p (some 4K TVs only offer 24 Hz in 1080p). Returns `null` when nothing fits or the current
 * mode is already right.
 */
fun chooseDisplayMode(
    modes: List<DisplayModeSpec>,
    current: DisplayModeSpec,
    videoFrameRate: Float?,
    videoWidth: Int? = null,
    videoHeight: Int? = null,
    seamlessOnly: Boolean = false,
): DisplayModeChoice? {
    if (videoFrameRate == null || videoFrameRate <= 0f) return null

    fun DisplayModeSpec.sameResolution() = width == current.width && height == current.height

    fun DisplayModeSpec.isSeamless() = sameResolution() &&
        (
            id == current.id ||
                current.alternativeRefreshRates.any { abs(it - refreshRate) < REFRESH_TOLERANCE }
            )

    fun DisplayModeSpec.fits(multiple: Int) =
        abs(refreshRate - videoFrameRate * multiple) < REFRESH_TOLERANCE * multiple

    val minWidth = maxOf(videoWidth ?: 0, MIN_WIDTH)
    val minHeight = maxOf(videoHeight ?: 0, MIN_HEIGHT)
    val usable = if (seamlessOnly) modes.filter { it.isSeamless() } else modes
    val sameResolution = usable.filter { it.sameResolution() }
    val otherResolution =
        usable
            .filter { !it.sameResolution() && it.width >= minWidth && it.height >= minHeight }
            .sortedByDescending { it.width * it.height }

    val best =
        sameResolution.firstOrNull { it.fits(SAME_RATE) }
            ?: sameResolution.firstOrNull { it.fits(DOUBLE_RATE) }
            ?: otherResolution.firstOrNull { it.fits(SAME_RATE) }
            ?: otherResolution.firstOrNull { it.fits(DOUBLE_RATE) }
            ?: return null
    if (best.id == current.id) return null
    return DisplayModeChoice(best, seamless = best.isSeamless())
}
