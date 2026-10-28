package nl.jellycast.core.data

/**
 * Audio codecs (Jellyfin names: `eac3`, `truehd`, …) this device currently passes through over
 * HDMI. Implemented in `core/playback`; an interface so `core/data` does not depend on it. Can
 * change with another receiver, so ask again for every list.
 */
fun interface AudioPassthrough {
    // Briefly blocks while reading the audio output: call on the io dispatcher.
    fun codecs(): Set<String>
}
