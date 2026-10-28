package nl.jellycast.mobile.core.remote.androidtv

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Just enough DER to build a self-signed X.509 certificate; the platform has no API for it. */
internal object Der {
    private val sha256WithRsa =
        byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 1, 1, 0x0B)
    private val commonNameOid = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)
    private val nullValue = byteArrayOf(0x05, 0x00)
    private val serialNumber = byteArrayOf(0x10, 0x00)

    private const val TAG_INTEGER = 0x02
    private const val TAG_BIT_STRING = 0x03
    private const val TAG_UTF8_STRING = 0x0C
    private const val TAG_UTC_TIME = 0x17
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_SET = 0x31
    private const val TAG_VERSION = 0xA0
    private const val VERSION_3 = 2
    private const val SHORT_LENGTH_LIMIT = 0x80
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    /** [subjectPublicKeyInfo] is the key's X.509 encoding (`PublicKey.getEncoded()`). */
    fun tbsCertificate(
        commonName: String,
        subjectPublicKeyInfo: ByteArray,
        validFrom: Date,
        validUntil: Date,
    ): ByteArray {
        val name =
            sequence(set(sequence(commonNameOid + element(TAG_UTF8_STRING, commonName.toByteArray()))))
        return sequence(
            element(TAG_VERSION, integer(byteArrayOf(VERSION_3.toByte()))) +
                integer(serialNumber) +
                algorithm() +
                name +
                sequence(utcTime(validFrom) + utcTime(validUntil)) +
                name +
                subjectPublicKeyInfo,
        )
    }

    fun certificate(tbs: ByteArray, signature: ByteArray): ByteArray =
        sequence(tbs + algorithm() + element(TAG_BIT_STRING, byteArrayOf(0) + signature))

    private fun algorithm() = sequence(sha256WithRsa + nullValue)

    private fun element(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + length(content.size) + content

    private fun sequence(content: ByteArray) = element(TAG_SEQUENCE, content)

    private fun set(content: ByteArray) = element(TAG_SET, content)

    private fun integer(value: ByteArray): ByteArray {
        val negative = value.isNotEmpty() && value[0] < 0
        return element(TAG_INTEGER, if (negative) byteArrayOf(0) + value else value)
    }

    private fun utcTime(date: Date): ByteArray {
        val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return element(TAG_UTC_TIME, format.format(date).toByteArray())
    }

    private fun length(count: Int): ByteArray {
        if (count < SHORT_LENGTH_LIMIT) return byteArrayOf(count.toByte())
        val bytes = ByteArrayOutputStream()
        var shift = Int.SIZE_BITS - BITS_PER_BYTE
        var started = false
        while (shift >= 0) {
            val byte = (count shr shift) and BYTE_MASK
            if (byte != 0 || started) {
                bytes.write(byte)
                started = true
            }
            shift -= BITS_PER_BYTE
        }
        val encoded = bytes.toByteArray()
        return byteArrayOf((SHORT_LENGTH_LIMIT or encoded.size).toByte()) + encoded
    }
}
