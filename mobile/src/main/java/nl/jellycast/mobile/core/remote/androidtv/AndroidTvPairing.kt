package nl.jellycast.mobile.core.remote.androidtv

import java.io.IOException
import java.security.MessageDigest
import nl.jellycast.core.common.AppDispatchers

/** Why pairing stopped; any other failure is an [IOException] (the TV can't be reached). */
sealed class PairingException(message: String) : IOException(message) {
    class WrongCode : PairingException("The code doesn't match")

    class Refused : PairingException("The TV refused the pairing")
}

/**
 * One-time pairing with an Android TV (port 6467): the TV shows a six-character code, and the
 * phone proves it saw it with a hash over both certificates. After that the TV trusts this
 * phone's certificate on the remote port.
 */
interface AndroidTvPairing {
    /**
     * Connects, asks the TV to show a code, calls [onCodeShown] and sends what [awaitCode]
     * returns. Cancelling ends the pairing on the TV.
     */
    suspend fun pair(
        host: String,
        clientName: String,
        onCodeShown: () -> Unit,
        awaitCode: suspend () -> String,
    )
}

class DefaultAndroidTvPairing(
    private val identity: ClientIdentitySource,
    private val dispatchers: AppDispatchers,
) : AndroidTvPairing {
    override suspend fun pair(
        host: String,
        clientName: String,
        onCodeShown: () -> Unit,
        awaitCode: suspend () -> String,
    ) {
        val client = identity.load()
        TlsChannel.connect(host, PORT, client, dispatchers.io) { channel ->
            channel.send(pairingRequest(clientName))
            channel.expect(PairingField.PAIRING_REQUEST_ACK)
            channel.send(pairingOptions())
            channel.expect(PairingField.OPTIONS)
            channel.send(pairingConfiguration())
            channel.expect(PairingField.CONFIGURATION_ACK)
            onCodeShown()
            val server = channel.serverCertificate?.rsaComponents()
            val ownKey = client.certificate.rsaComponents()
            if (server == null || ownKey == null) throw PairingException.Refused()
            val secret =
                pairingSecret(awaitCode(), ownKey, server) ?: throw PairingException.WrongCode()
            channel.send(pairingMessage { message(PairingField.SECRET) { bytes(1, secret) } })
            channel.expect(PairingField.SECRET_ACK)
        }
    }

    private suspend fun TlsChannel.expect(field: Int) {
        val message = receive()
        when (message.varint(PairingField.STATUS)) {
            STATUS_OK -> if (!message.has(field)) throw PairingException.Refused()
            STATUS_BAD_SECRET -> throw PairingException.WrongCode()
            else -> throw PairingException.Refused()
        }
    }

    companion object {
        const val PORT = 6467
    }
}

private object PairingField {
    const val PROTOCOL_VERSION = 1
    const val STATUS = 2
    const val PAIRING_REQUEST = 10
    const val PAIRING_REQUEST_ACK = 11
    const val OPTIONS = 20
    const val CONFIGURATION = 30
    const val CONFIGURATION_ACK = 31
    const val SECRET = 40
    const val SECRET_ACK = 41
}

private const val PROTOCOL_VERSION = 2L
private const val STATUS_OK = 200L
private const val STATUS_BAD_SECRET = 402L
private const val ENCODING_HEXADECIMAL = 3L
private const val CODE_SYMBOLS = 6L
private const val ROLE_INPUT = 1L
private const val SERVICE_NAME = "atvremote"
private const val CODE_BYTES = 3
private const val HEX_RADIX = 16

internal fun pairingMessage(build: ProtoWriter.() -> Unit): ProtoWriter = protoMessage {
    varint(PairingField.PROTOCOL_VERSION, PROTOCOL_VERSION)
    varint(PairingField.STATUS, STATUS_OK)
    build()
}

internal fun pairingRequest(clientName: String) = pairingMessage {
    message(PairingField.PAIRING_REQUEST) {
        string(1, SERVICE_NAME)
        string(2, clientName)
    }
}

internal fun pairingOptions() = pairingMessage {
    message(PairingField.OPTIONS) {
        message(1) { hexEncoding() }
        varint(3, ROLE_INPUT)
    }
}

internal fun pairingConfiguration() = pairingMessage {
    message(PairingField.CONFIGURATION) {
        message(1) { hexEncoding() }
        varint(2, ROLE_INPUT)
    }
}

private fun ProtoWriter.hexEncoding() {
    varint(1, ENCODING_HEXADECIMAL)
    varint(2, CODE_SYMBOLS)
}

/**
 * SHA-256 over client modulus and exponent, server modulus and exponent, and the code after its
 * first byte; that first byte is a check digit and must equal the hash's first byte.
 */
internal fun pairingSecret(
    code: String,
    client: Pair<ByteArray, ByteArray>,
    server: Pair<ByteArray, ByteArray>,
): ByteArray? {
    val bytes = code.uppercase().chunked(2).map { it.toIntOrNull(HEX_RADIX) ?: return null }
    if (bytes.size != CODE_BYTES || code.length != CODE_BYTES * 2) return null
    val digest =
        MessageDigest.getInstance("SHA-256").run {
            update(client.first)
            update(client.second)
            update(server.first)
            update(server.second)
            update(bytes.drop(1).map { it.toByte() }.toByteArray())
            digest()
        }
    return digest.takeIf { it[0] == bytes[0].toByte() }
}
