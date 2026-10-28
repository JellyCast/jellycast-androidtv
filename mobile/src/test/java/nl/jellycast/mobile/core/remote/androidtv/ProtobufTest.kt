package nl.jellycast.mobile.core.remote.androidtv

import java.io.ByteArrayInputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtobufTest {
    @Test
    fun `a varint field is its tag and the value in groups of seven bits`() {
        val bytes = protoMessage { varint(1, 300) }.toByteArray()

        assertArrayEquals(bytes(0x08, 0xAC, 0x02), bytes)
    }

    @Test
    fun `a nested message is length delimited`() {
        val bytes = protoMessage { message(10) { varint(1, 26) } }.toByteArray()

        assertArrayEquals(bytes(0x52, 0x02, 0x08, 0x1A), bytes)
    }

    @Test
    fun `a framed message starts with its length`() {
        val framed = protoMessage { string(2, "abc") }.framed()

        assertArrayEquals(bytes(0x05, 0x12, 0x03, 'a'.code, 'b'.code, 'c'.code), framed)
    }

    @Test
    fun `parsing reads back varints, strings and nested messages`() {
        val bytes =
            protoMessage {
                varint(2, 200)
                message(40) { varint(1, 1) }
                string(5, "atvremote")
            }.toByteArray()

        val message = ProtoMessage.parse(bytes)

        requireNotNull(message)
        assertEquals(200L, message.varint(2))
        assertEquals(1L, message.message(40)?.varint(1))
        assertEquals("atvremote", message.bytes(5)?.decodeToString())
        assertFalse(message.has(3))
    }

    @Test
    fun `unknown fixed width fields are skipped`() {
        val bytes = bytes(0x09, 1, 2, 3, 4, 5, 6, 7, 8, 0x15, 1, 2, 3, 4, 0x18, 0x07)

        val message = ProtoMessage.parse(bytes)

        assertEquals(7L, message?.varint(3))
    }

    @Test
    fun `a truncated message is unreadable`() {
        assertNull(ProtoMessage.parse(bytes(0x12, 0x05, 0x01)))
    }

    @Test
    fun `framed messages are read one after another from a stream`() {
        val stream =
            ByteArrayInputStream(
                protoMessage { varint(1, 1) }.framed() + protoMessage { varint(2, 2) }.framed(),
            )

        assertEquals(1L, stream.readFramedMessage().varint(1))
        assertEquals(2L, stream.readFramedMessage().varint(2))
    }

    @Test(expected = EOFException::class)
    fun `an ended stream throws`() {
        ByteArrayInputStream(bytes(0x05, 0x08)).readFramedMessage()
    }

    @Test
    fun `an empty message has no fields`() {
        val message = ProtoMessage.parse(ByteArray(0))

        assertTrue(message != null && !message.has(1))
    }
}

internal fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
