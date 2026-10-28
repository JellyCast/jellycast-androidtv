package nl.jellycast.mobile.core.remote.androidtv

import java.security.MessageDigest
import java.util.Date
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidTvMessagesTest {
    private val client = RemoteClientInfo("Pixel 9", "Google", "JellyCast Pixel 9 AB12")

    @Test
    fun `the phone answers configure with its features and device info`() {
        val configure = ProtoMessage.parse(
            protoMessage {
                message(Field.CONFIGURE) { varint(1, 622) }
            }.toByteArray(),
        )

        val reply = remoteReply(requireNotNull(configure), client, AndroidTvRemote.FEATURES)

        val answer = ProtoMessage.parse(
            requireNotNull(reply).toByteArray(),
        )?.message(Field.CONFIGURE)
        assertEquals(AndroidTvRemote.FEATURES, answer?.varint(1))
        val info = answer?.message(2)
        assertEquals("Pixel 9", info?.bytes(1)?.decodeToString())
        assertEquals("Google", info?.bytes(2)?.decodeToString())
        assertEquals("atvremote", info?.bytes(5)?.decodeToString())
    }

    @Test
    fun `a ping is answered with the same id`() {
        val ping = ProtoMessage.parse(
            protoMessage {
                message(Field.PING_REQUEST) { varint(1, 42) }
            }.toByteArray(),
        )

        val reply = remoteReply(requireNotNull(ping), client, AndroidTvRemote.FEATURES)

        val answer = ProtoMessage.parse(requireNotNull(reply).toByteArray())
        assertEquals(42L, answer?.message(Field.PING_RESPONSE)?.varint(1))
    }

    @Test
    fun `a start message needs no answer`() {
        val start = ProtoMessage.parse(
            protoMessage {
                message(Field.START) { varint(1, 1) }
            }.toByteArray(),
        )

        assertNull(remoteReply(requireNotNull(start), client, AndroidTvRemote.FEATURES))
    }

    @Test
    fun `a key press is the key code with a short press`() {
        assertArrayEquals(bytes(0x52, 0x04, 0x08, 0x1A, 0x10, 0x03), keyPress(26).toByteArray())
    }

    @Test
    fun `a pairing request carries the protocol version, status and client name`() {
        val message = ProtoMessage.parse(pairingRequest("JellyCast Pixel").toByteArray())

        assertEquals(2L, message?.varint(1))
        assertEquals(200L, message?.varint(2))
        assertEquals("atvremote", message?.message(10)?.bytes(1)?.decodeToString())
        assertEquals("JellyCast Pixel", message?.message(10)?.bytes(2)?.decodeToString())
    }

    @Test
    fun `pairing options ask for a six-character hexadecimal code as input`() {
        val options = ProtoMessage.parse(pairingOptions().toByteArray())?.message(20)

        assertEquals(3L, options?.message(1)?.varint(1))
        assertEquals(6L, options?.message(1)?.varint(2))
        assertEquals(1L, options?.varint(3))
    }

    @Test
    fun `the volume message reads level, maximum and mute`() {
        val volume = ProtoMessage.parse(
            protoMessage {
                varint(6, 100)
                varint(7, 35)
                varint(8, 1)
            }.toByteArray(),
        )

        assertEquals(TvVolume(level = 35, max = 100, isMuted = true), volume?.toTvVolume())
    }

    @Test
    fun `the pairing secret hashes both keys and the code after its check byte`() {
        val clientKey = bytes(0x01, 0x02) to bytes(0x01, 0x00, 0x01)
        val serverKey = bytes(0x03, 0x04) to bytes(0x01, 0x00, 0x01)
        val tail = bytes(0xAB, 0xCD)
        val digest =
            MessageDigest.getInstance("SHA-256").run {
                listOf(clientKey.first, clientKey.second, serverKey.first, serverKey.second, tail)
                    .forEach(::update)
                digest()
            }
        val code = "%02X".format(digest[0]) + "abcd"

        assertArrayEquals(digest, pairingSecret(code, clientKey, serverKey))
    }

    @Test
    fun `a code whose check byte doesn't match gives no secret`() {
        val clientKey = bytes(0x01) to bytes(0x03)
        val serverKey = bytes(0x02) to bytes(0x03)
        val right = requireNotNull(validCode(clientKey, serverKey))
        val wrongFirst = (right.substring(0, 2).toInt(16) xor 1).let { "%02X".format(it) }

        assertNull(pairingSecret(wrongFirst + right.substring(2), clientKey, serverKey))
        assertNull(pairingSecret("12345", clientKey, serverKey))
        assertNull(pairingSecret("12345G", clientKey, serverKey))
    }

    @Test
    fun `the client certificate is self-signed and its key has no leading zero`() {
        val identity = generateClientIdentity(Date(0))

        identity.certificate.verify(identity.certificate.publicKey)
        assertEquals("CN=JellyCast", identity.certificate.subjectX500Principal.name)
        val (modulus, exponent) = requireNotNull(identity.certificate.rsaComponents())
        assertEquals(256, modulus.size)
        assertNotEquals(0.toByte(), modulus[0])
        assertArrayEquals(bytes(0x01, 0x00, 0x01), exponent)
    }

    @Test
    fun `the play link carries the item, start and tracks`() {
        assertEquals(
            "jellycast://play?item=abc&start=90000&audio=1&subtitle=-1",
            remotePlayLink("abc", 90_000, 1, -1),
        )
        assertEquals("jellycast://play?item=abc&start=0", remotePlayLink("abc", -5, null, null))
    }

    private fun validCode(
        client: Pair<ByteArray, ByteArray>,
        server: Pair<ByteArray, ByteArray>,
    ): String? = (0..255).map { "%02X".format(it) + "00FF" }
        .firstOrNull { pairingSecret(it, client, server) != null }
}
