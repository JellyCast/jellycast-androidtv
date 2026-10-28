package nl.jellycast.tv.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioProfile
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A measurement, not a test with an outcome; it always passes. Can this device take IEC61937 with
 * 8 channels at 192 kHz (HBR), the form in which TrueHD (MAT) and DTS-HD MA go to an amplifier?
 * The audio HAL does not report TrueHD passthrough itself, but does report `AUDIO_FORMAT_IEC61937`.
 *
 * Never calls `play()` or writes data, because someone may be watching something else. Results go
 * to logcat (`adb logcat -d -s IecProbe`) and to the `am instrument` status output.
 */
@RunWith(AndroidJUnit4::class)
class Iec61937ProbeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val lines = mutableListOf<String>()

    // Same attributes as the player uses for movies.
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

    @Test
    fun probe() {
        logOutputDevices()
        logDirectProfiles()
        logDirectSupport()
        tryCreateTrack(
            AudioFormat.ENCODING_IEC61937,
            AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
            192_000,
        )
        tryCreateTrack(AudioFormat.ENCODING_IEC61937, AudioFormat.CHANNEL_OUT_STEREO, 192_000)
        tryCreateTrack(AudioFormat.ENCODING_IEC61937, AudioFormat.CHANNEL_OUT_STEREO, 48_000)
        report()
    }

    private fun logOutputDevices() {
        val devices = audioManager.getAudioDevicesForAttributes(attributes)
        log("outputs for movie attributes: ${devices.size}")
        devices.forEach { device ->
            log(
                "output type=${device.type} (${typeName(
                    device.type,
                )}) name=${device.productName} " +
                    "encodings=${device.encodings.joinToString { encodingName(it) }} " +
                    "channels=${device.channelCounts.toList()} " +
                    "rates=${device.sampleRates.toList()}",
            )
        }
        // The full profiles live on the AudioDeviceInfo from getDevices (same id).
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        devices.forEach { attr ->
            outputs.filter { it.type == attr.type }.forEach { device ->
                log(
                    "output id=${device.id} type=${typeName(device.type)} " +
                        "encodings=${device.encodings.joinToString { encodingName(it) }} " +
                        "channels=${device.channelCounts.toList()} " +
                        "rates=${device.sampleRates.toList()}",
                )
                device.audioProfiles.forEach { log("  profile ${describe(it)}") }
            }
        }
    }

    private fun logDirectProfiles() {
        val profiles = audioManager.getDirectProfilesForAttributes(attributes)
        log("getDirectProfilesForAttributes: ${profiles.size} profiles")
        profiles.forEach { log("  direct ${describe(it)}") }
    }

    private fun logDirectSupport() {
        val iec = AudioFormat.ENCODING_IEC61937
        val surround71 = AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
        val stereo = AudioFormat.CHANNEL_OUT_STEREO
        listOf(
            Triple(iec, stereo, 48_000),
            Triple(iec, stereo, 192_000),
            Triple(iec, surround71, 192_000),
            Triple(iec, surround71, 48_000),
            Triple(AudioFormat.ENCODING_DOLBY_TRUEHD, surround71, 48_000),
            Triple(AudioFormat.ENCODING_DOLBY_MAT, surround71, 48_000),
            Triple(AudioFormat.ENCODING_DTS_HD, surround71, 48_000),
            Triple(AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.CHANNEL_OUT_5POINT1, 48_000),
        ).forEach { (encoding, mask, rate) ->
            val format = format(encoding, mask, rate)
            val direct = AudioTrack.isDirectPlaybackSupported(format, attributes)
            val support = AudioManager.getDirectPlaybackSupport(format, attributes)
            log(
                "SUPPORT ${encodingName(encoding)} ${Integer.bitCount(mask)}ch $rate Hz: " +
                    "isDirectPlaybackSupported=$direct getDirectPlaybackSupport=$support " +
                    "(${supportName(support)})",
            )
        }
    }

    /** Creates and immediately releases an AudioTrack: never `play()`, never `write()`. */
    private fun tryCreateTrack(encoding: Int, mask: Int, rate: Int) {
        val label = "${encodingName(encoding)} ${Integer.bitCount(mask)}ch $rate Hz"
        // IEC61937 is 16-bit per channel; a few frames of an HBR burst are plenty.
        val bufferBytes = Integer.bitCount(mask) * 2 * 8192
        val track =
            try {
                AudioTrack
                    .Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format(encoding, mask, rate))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            } catch (e: UnsupportedOperationException) {
                log("TRACK $label: create failed: ${e.javaClass.simpleName}: ${e.message}")
                return
            } catch (e: IllegalArgumentException) {
                log("TRACK $label: create failed: ${e.javaClass.simpleName}: ${e.message}")
                return
            }
        try {
            val routed = track.routedDevice
            log(
                "TRACK $label: created, state=${track.state} rate=${track.sampleRate} " +
                    "channels=${track.channelCount} encoding=${encodingName(track.audioFormat)} " +
                    "buffer=${track.bufferSizeInFrames} frames " +
                    "route=${routed?.let { typeName(it.type) }}",
            )
        } finally {
            track.release()
        }
    }

    private fun format(encoding: Int, mask: Int, rate: Int): AudioFormat = AudioFormat
        .Builder()
        .setEncoding(encoding)
        .setChannelMask(mask)
        .setSampleRate(rate)
        .build()

    private fun describe(profile: AudioProfile): String =
        "format=${encodingName(profile.format)} " +
            "masks=${profile.channelMasks.map { "0x${Integer.toHexString(it)}" }} " +
            "indexMasks=${profile.channelIndexMasks.map { "0x${Integer.toHexString(it)}" }} " +
            "rates=${profile.sampleRates.toList()} encapsulation=${profile.encapsulationType}"

    private fun log(line: String) {
        Log.i(TAG, line)
        lines += line
    }

    private fun report() {
        val bundle = Bundle()
        lines.forEachIndexed { index, line -> bundle.putString("probe_%03d".format(index), line) }
        instrumentation.sendStatus(0, bundle)
    }

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI_ARC"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI_EARC"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        else -> "type$type"
    }

    private fun encodingName(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
        AudioFormat.ENCODING_AC3 -> "AC3"
        AudioFormat.ENCODING_E_AC3 -> "EAC3"
        AudioFormat.ENCODING_E_AC3_JOC -> "EAC3_JOC"
        AudioFormat.ENCODING_DTS -> "DTS"
        AudioFormat.ENCODING_DTS_HD -> "DTS_HD"
        AudioFormat.ENCODING_DOLBY_TRUEHD -> "TRUEHD"
        AudioFormat.ENCODING_DOLBY_MAT -> "MAT"
        AudioFormat.ENCODING_IEC61937 -> "IEC61937"
        AudioFormat.ENCODING_AC4 -> "AC4"
        else -> "enc$encoding"
    }

    private fun supportName(support: Int): String = buildList {
        if (support == AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED) add("NOT_SUPPORTED")
        if (support and AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED != 0) add("OFFLOAD")
        if (support and AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED != 0) add("BITSTREAM")
    }.joinToString("|")

    private companion object {
        const val TAG = "IecProbe"
    }
}
