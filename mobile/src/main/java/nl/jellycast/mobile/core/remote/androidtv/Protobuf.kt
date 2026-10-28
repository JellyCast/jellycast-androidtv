package nl.jellycast.mobile.core.remote.androidtv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * The few protobuf messages of the Android TV remote protocol, written by hand: a handful of
 * message types don't justify a code generator and a dependency.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) {
        tag(field, WIRE_VARINT)
        writeVarint(value, out)
    }

    fun bytes(field: Int, value: ByteArray) {
        tag(field, WIRE_LENGTH_DELIMITED)
        writeVarint(value.size.toLong(), out)
        out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.encodeToByteArray())

    fun message(field: Int, build: ProtoWriter.() -> Unit) =
        bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    /** The protocol sends every message after its length as a varint. */
    fun framed(): ByteArray {
        val body = toByteArray()
        val framed = ByteArrayOutputStream(body.size + MAX_VARINT_BYTES)
        writeVarint(body.size.toLong(), framed)
        framed.write(body)
        return framed.toByteArray()
    }

    private fun tag(field: Int, wireType: Int) {
        writeVarint((field.toLong() shl TAG_SHIFT) or wireType.toLong(), out)
    }
}

fun protoMessage(build: ProtoWriter.() -> Unit): ProtoWriter = ProtoWriter().apply(build)

/** The fields of one message; unknown fields and wire types are skipped. */
class ProtoMessage private constructor(
    private val varints: Map<Int, Long>,
    private val payloads: Map<Int, ByteArray>,
) {
    fun has(field: Int): Boolean = field in varints || field in payloads

    fun varint(field: Int): Long? = varints[field]

    fun bytes(field: Int): ByteArray? = payloads[field]

    fun message(field: Int): ProtoMessage? = payloads[field]?.let(::parse)

    companion object {
        fun parse(bytes: ByteArray): ProtoMessage? {
            val varints = mutableMapOf<Int, Long>()
            val payloads = mutableMapOf<Int, ByteArray>()
            val cursor = Cursor(bytes)
            while (cursor.hasMore) {
                val key = cursor.varint() ?: return null
                val field = (key ushr TAG_SHIFT).toInt()
                when ((key and WIRE_TYPE_MASK).toInt()) {
                    WIRE_VARINT -> varints[field] = cursor.varint() ?: return null

                    WIRE_LENGTH_DELIMITED -> {
                        val length = cursor.varint() ?: return null
                        payloads[field] = cursor.take(length) ?: return null
                    }

                    WIRE_FIXED64 -> cursor.skip(FIXED64_BYTES) || return null

                    WIRE_FIXED32 -> cursor.skip(FIXED32_BYTES) || return null

                    else -> return null
                }
            }
            return ProtoMessage(varints, payloads)
        }
    }
}

/** Reads one length-prefixed message; throws [EOFException] when the stream ends. */
fun InputStream.readFramedMessage(): ProtoMessage {
    val length = readVarint()
    if (length !in 0..MAX_MESSAGE_BYTES) throw IOException("Message of $length bytes")
    val body = readNBytes(length.toInt())
    if (body.size.toLong() != length) throw EOFException()
    return ProtoMessage.parse(body) ?: throw IOException("Unreadable message")
}

private fun InputStream.readVarint(): Long {
    var result = 0L
    var shift = 0
    while (shift < Long.SIZE_BITS) {
        val byte = read()
        if (byte < 0) throw EOFException()
        result = result or ((byte and VARINT_PAYLOAD).toLong() shl shift)
        if (byte and VARINT_CONTINUE == 0) return result
        shift += VARINT_BITS
    }
    throw IOException("Varint too long")
}

internal fun writeVarint(value: Long, out: ByteArrayOutputStream) {
    var rest = value
    while (rest and VARINT_PAYLOAD.toLong().inv() != 0L) {
        out.write(((rest and VARINT_PAYLOAD.toLong()) or VARINT_CONTINUE.toLong()).toInt())
        rest = rest ushr VARINT_BITS
    }
    out.write(rest.toInt())
}

private class Cursor(private val bytes: ByteArray) {
    private var index = 0

    val hasMore: Boolean get() = index < bytes.size

    fun varint(): Long? {
        var result = 0L
        var shift = 0
        while (index < bytes.size && shift < Long.SIZE_BITS) {
            val byte = bytes[index++].toInt() and BYTE_MASK
            result = result or ((byte and VARINT_PAYLOAD).toLong() shl shift)
            if (byte and VARINT_CONTINUE == 0) return result
            shift += VARINT_BITS
        }
        return null
    }

    fun take(length: Long): ByteArray? {
        if (length < 0 || length > bytes.size - index) return null
        val end = index + length.toInt()
        return bytes.copyOfRange(index, end).also { index = end }
    }

    fun skip(count: Int): Boolean {
        if (count > bytes.size - index) return false
        index += count
        return true
    }
}

private const val WIRE_VARINT = 0
private const val WIRE_FIXED64 = 1
private const val WIRE_LENGTH_DELIMITED = 2
private const val WIRE_FIXED32 = 5
private const val WIRE_TYPE_MASK = 7L
private const val TAG_SHIFT = 3
private const val FIXED64_BYTES = 8
private const val FIXED32_BYTES = 4
private const val VARINT_PAYLOAD = 0x7F
private const val VARINT_CONTINUE = 0x80
private const val VARINT_BITS = 7
private const val MAX_VARINT_BYTES = 10
private const val BYTE_MASK = 0xFF

// The TV's messages are a few hundred bytes; anything this large is a broken stream.
private const val MAX_MESSAGE_BYTES = 1L shl 20
