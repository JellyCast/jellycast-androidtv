package nl.jellycast.core.playback

/**
 * With decoder fallback Media3 picks `c2.android.hevc.decoder` when the hardware decoder is still
 * busy; it cannot do 4K and shows 10-bit HDR with green blocks. An error is better: the fallback
 * then restarts the hardware decoder.
 */
fun <T> withoutSoftwareVideoFallback(decoders: List<T>, softwareOnly: (T) -> Boolean): List<T> =
    decoders.filterNot(softwareOnly).ifEmpty { decoders }
