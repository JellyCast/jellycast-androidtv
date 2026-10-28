package nl.jellycast.mobile.core.remote.androidtv

import java.io.IOException
import kotlinx.coroutines.delay

/** The TV's volume as it reports it; with HDMI-CEC this may be the Chromecast's own level. */
data class TvVolume(val level: Int, val max: Int, val isMuted: Boolean)

enum class TvKey(internal val code: Long) {
    VolumeUp(AndroidTvRemote.KEY_VOLUME_UP),
    VolumeDown(AndroidTvRemote.KEY_VOLUME_DOWN),
    Mute(AndroidTvRemote.KEY_MUTE),
}

/** A remote connection that stays open while a screen needs it, for the volume keys. */
interface AndroidTvControl {
    /** Connects and answers the TV until the connection ends or the caller is cancelled. */
    suspend fun run(host: String, onReady: () -> Unit, onVolume: (TvVolume) -> Unit)

    suspend fun press(key: TvKey)
}

class DefaultAndroidTvControl(private val remote: AndroidTvRemote) : AndroidTvControl {
    @Volatile
    private var channel: TlsChannel? = null

    override suspend fun run(host: String, onReady: () -> Unit, onVolume: (TvVolume) -> Unit) {
        val features = AndroidTvRemote.FEATURES or AndroidTvRemote.VOLUME_FEATURE
        try {
            remote.connect(host) { channel ->
                remote.handshake(channel, features)
                delay(AndroidTvRemote.InputReady)
                this.channel = channel
                onReady()
                while (true) {
                    val message = channel.receive()
                    val volume = message.message(Field.VOLUME_LEVEL)
                    if (volume != null) {
                        onVolume(volume.toTvVolume())
                    } else {
                        remote.respond(message, channel, features)
                    }
                }
            }
        } finally {
            channel = null
        }
    }

    override suspend fun press(key: TvKey) {
        val open = channel ?: throw IOException("Not connected")
        remote.press(open, key.code)
    }
}

private object VolumeField {
    const val MAX = 6
    const val LEVEL = 7
    const val MUTED = 8
}

internal fun ProtoMessage.toTvVolume() = TvVolume(
    level = (varint(VolumeField.LEVEL) ?: 0).toInt(),
    max = (varint(VolumeField.MAX) ?: 0).toInt(),
    isMuted = varint(VolumeField.MUTED) == 1L,
)
