package nl.jellycast.mobile.core.remote

import kotlinx.serialization.Serializable
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.SubtitleTrack

data class RemoteItem(
    val id: String,
    val name: String,
    /** Only for an episode. */
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val runtimeMs: Long? = null,
    /** An episode's own still; a movie's backdrop, or its poster without one. */
    val imageUrl: String? = null,
    val thumbUrl: String? = null,
)

/** Something playing on another device under the same account, as the server last heard it. */
data class RemoteSession(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val item: RemoteItem,
    val positionMs: Long,
    val isPaused: Boolean,
    val playMethod: PlayMethod?,
    val checkedInAtMs: Long?,
    /** The TV app listens for commands; without it the session can only be watched. */
    val canControl: Boolean,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
) {
    fun isSilentAt(nowMs: Long): Boolean =
        checkedInAtMs != null && nowMs - checkedInAtMs > SILENCE_BEFORE_STOPPED_MS

    /** The position now: the reported one, run on since the report while playing. */
    fun positionAt(nowMs: Long): Long {
        if (isPaused || checkedInAtMs == null) return positionMs
        val elapsed = (nowMs - checkedInAtMs).coerceIn(0, MAX_EXTRAPOLATION_MS)
        val position = positionMs + elapsed
        return runtimeCap(position)
    }

    fun remainingMsAt(nowMs: Long): Long? =
        item.runtimeMs?.let { (it - positionAt(nowMs)).coerceAtLeast(0) }

    fun progressAt(nowMs: Long): Float {
        val runtime = item.runtimeMs ?: return 0f
        if (runtime <= 0) return 0f
        return positionAt(nowMs).toFloat() / runtime
    }

    private fun runtimeCap(position: Long): Long = item.runtimeMs?.let { minOf(position, it) }
        ?: position

    private companion object {
        const val MAX_EXTRAPOLATION_MS = 30_000L

        // Our TV apps report every 10 s, paused or not; three missed reports mean the app is
        // gone (closed, crashed, TV off) without the stop report that clears it on the server.
        const val SILENCE_BEFORE_STOPPED_MS = 30_000L
    }
}

/** A TV app that takes commands right now, playing or not. */
data class RemoteDevice(
    /** The session id: commands go to the session. */
    val id: String,
    val deviceId: String,
    val name: String,
    val isPlaying: Boolean,
    val lastActivityMs: Long?,
    /** Its address on the home network, as the server saw it. */
    val address: String?,
    /** Only an Android TV can be woken from the phone. */
    val isAndroidTv: Boolean,
)

data class RemoteSnapshot(val playing: List<RemoteSession>, val devices: List<RemoteDevice>)

/** An Android TV this phone paired with: it can be woken even when JellyCast on it is closed. */
@Serializable
data class PairedTv(
    /** The Jellyfin device id of its JellyCast app, to recognise its session. */
    val deviceId: String,
    val host: String,
    val name: String,
    /** The TV no longer accepts this phone's certificate: pair again before it can be woken. */
    val needsPairing: Boolean = false,
)

/** A Chromecast to pair: one with JellyCast open, or one paired before whose address is known. */
data class PairingTarget(val deviceId: String, val host: String, val name: String)

fun RemoteDevice.pairingTarget(): PairingTarget? = address?.let {
    PairingTarget(deviceId, it, name)
}

fun PairedTv.pairingTarget(): PairingTarget = PairingTarget(deviceId, host, name)

data class RemoteTrack(val index: Int, val title: String)

/** The item's tracks by the server's stream index; subtitles sort by [preferredLanguages]. */
data class RemoteTracks(
    val audio: List<RemoteTrack>,
    val subtitles: List<SubtitleTrack>,
    val preferredLanguages: List<String> = emptyList(),
)
