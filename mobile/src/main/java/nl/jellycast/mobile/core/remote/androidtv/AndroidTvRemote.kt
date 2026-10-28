package nl.jellycast.mobile.core.remote.androidtv

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nl.jellycast.core.common.AppDispatchers

/**
 * How this phone introduces itself to the TV. The TV keeps one pairing per [pairingName], so
 * every phone needs its own: the same name for two devices made the second replace the first.
 */
data class RemoteClientInfo(val model: String, val vendor: String, val pairingName: String)

/** What the app does with a paired Android TV, the way the Google TV app does. */
interface AndroidTvLink {
    /** Whether the TV's remote port answers from where the phone is now. */
    suspend fun isReachable(host: String): Boolean

    /** Wakes the TV, turns the TV set on and opens [link] on it. */
    suspend fun wakeAndOpen(host: String, link: String)

    /** Power, only when the TV reports it is on; the TV set follows over HDMI. */
    suspend fun turnOff(host: String)
}

class AndroidTvRemote(
    private val identity: ClientIdentitySource,
    private val client: RemoteClientInfo,
    private val dispatchers: AppDispatchers,
) : AndroidTvLink {
    override suspend fun isReachable(host: String): Boolean = withContext(dispatchers.io) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, PORT), PROBE_TIMEOUT_MS) }
            true
        } catch (_: IOException) {
            false
        }
    }

    override suspend fun wakeAndOpen(host: String, link: String) = withDeadline {
        connect(host) { channel ->
            val isOn = handshake(channel)
            delay(InputReady)
            if (!isOn) {
                press(channel, KEY_POWER)
                waitUntilOn(channel)
            }
            // The Chromecast stays awake when only the TV was turned off, and opening a link
            // sends nothing over HDMI-CEC. Home does: One Touch Play turns the TV on.
            press(channel, KEY_HOME)
            delay(HomeSettle)
            channel.send(protoMessage { message(Field.APP_LINK) { string(1, link) } })
            delay(LinkSettle)
        }
    }

    override suspend fun turnOff(host: String) = withDeadline {
        connect(host) { channel ->
            if (!handshake(channel)) return@connect
            delay(InputReady)
            press(channel, KEY_POWER)
            delay(LinkSettle)
        }
    }

    /** A timeout is an [IOException] here, so callers don't mistake it for being cancelled. */
    private suspend fun withDeadline(block: suspend () -> Unit) {
        withTimeoutOrNull(Deadline) { block() }
            ?: throw IOException("The TV did not answer in time")
    }

    internal suspend fun <T> connect(host: String, block: suspend (TlsChannel) -> T): T =
        TlsChannel.connect(host, PORT, identity.load(), dispatchers.io, block)

    /** Answers the TV's configure and set-active messages until it says whether it is on. */
    internal suspend fun handshake(channel: TlsChannel, features: Long = FEATURES): Boolean {
        while (true) {
            respond(channel.receive(), channel, features)?.let { return it }
        }
    }

    private suspend fun waitUntilOn(channel: TlsChannel) {
        while (true) {
            if (respond(channel.receive(), channel) == true) return
        }
    }

    /** The on/off state when the message carries it, otherwise `null` after answering it. */
    internal suspend fun respond(
        message: ProtoMessage,
        channel: TlsChannel,
        features: Long = FEATURES,
    ): Boolean? {
        val reply = remoteReply(message, client, features)
        if (reply != null) channel.send(reply)
        return message.message(Field.START)?.let { it.varint(1) == 1L }
    }

    internal suspend fun press(channel: TlsChannel, key: Long) = channel.send(keyPress(key))

    companion object {
        const val PORT = 6466
        const val KEY_VOLUME_UP = 24L
        const val KEY_VOLUME_DOWN = 25L
        const val KEY_MUTE = 164L

        /** Ping, keys, power and app links. */
        const val FEATURES = 1L or 2L or 32L or 512L

        /** Asks the TV to report its volume as well. */
        const val VOLUME_FEATURE = 64L

        // Power, not WAKEUP: asleep, the TV's remote service ignores WAKEUP. Only sent when the
        // TV itself reports it is off, so it never switches it off.
        private const val KEY_POWER = 26L
        private const val KEY_HOME = 3L
        private const val PROBE_TIMEOUT_MS = 1_500

        // Waking, Home and the link together; a sleeping Chromecast is up within a few seconds.
        private val Deadline = 8.seconds
        private val LinkSettle = 800.milliseconds

        // Lets One Touch Play reach the TV before the link replaces the home screen.
        private val HomeSettle = 700.milliseconds

        // The TV registers its virtual remote only after the handshake; earlier keys are lost.
        internal val InputReady = 600.milliseconds
    }
}

internal object Field {
    const val CONFIGURE = 1
    const val SET_ACTIVE = 2
    const val PING_REQUEST = 8
    const val PING_RESPONSE = 9
    const val KEY_INJECT = 10
    const val START = 40
    const val VOLUME_LEVEL = 50
    const val APP_LINK = 90
}

private const val SHORT_PRESS = 3L
private const val CLIENT_PACKAGE = "atvremote"
private const val CLIENT_VERSION = "1.0.0"

/** What the phone answers to a message from the TV, or `null` when it needs no answer. */
internal fun remoteReply(
    message: ProtoMessage,
    client: RemoteClientInfo,
    features: Long,
): ProtoWriter? = when {
    message.has(Field.CONFIGURE) ->
        protoMessage {
            message(Field.CONFIGURE) {
                varint(1, features)
                message(2) {
                    string(1, client.model)
                    string(2, client.vendor)
                    varint(3, 1)
                    string(4, "1")
                    string(5, CLIENT_PACKAGE)
                    string(6, CLIENT_VERSION)
                }
            }
        }

    message.has(Field.SET_ACTIVE) ->
        protoMessage { message(Field.SET_ACTIVE) { varint(1, features) } }

    message.has(Field.PING_REQUEST) -> {
        val id = message.message(Field.PING_REQUEST)?.varint(1) ?: 0
        protoMessage { message(Field.PING_RESPONSE) { varint(1, id) } }
    }

    else -> null
}

internal fun keyPress(key: Long): ProtoWriter = protoMessage {
    message(Field.KEY_INJECT) {
        varint(1, key)
        varint(2, SHORT_PRESS)
    }
}

/** `jellycast://play?item=…&start=<ms>&audio=…&subtitle=…`, which the TV app opens the player for. */
fun remotePlayLink(
    itemId: String,
    startMs: Long,
    audioStreamIndex: Int?,
    subtitleStreamIndex: Int?,
): String = buildString {
    append("jellycast://play?item=").append(itemId)
    append("&start=").append(startMs.coerceAtLeast(0))
    audioStreamIndex?.let { append("&audio=").append(it) }
    subtitleStreamIndex?.let { append("&subtitle=").append(it) }
}
