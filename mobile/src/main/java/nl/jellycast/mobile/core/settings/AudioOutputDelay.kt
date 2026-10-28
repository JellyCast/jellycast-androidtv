package nl.jellycast.mobile.core.settings

import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** A Bluetooth audio output, by a key that stays the same between connections. */
data class AudioDevice(val id: String, val name: String)

data class SavedAudioDelay(val name: String, val milliseconds: Int)

/**
 * The lip-sync correction per Bluetooth device, set once in the player and applied whenever that
 * device plays. Car stereos often report less latency than they add, so the picture runs ahead of
 * the sound even though Media3 corrects for what the device reports.
 */
interface AudioOutputDelay {
    /** The Bluetooth device playing now, `null` on the speaker and wired outputs. */
    val currentDevice: StateFlow<AudioDevice?>

    val saved: StateFlow<Map<String, SavedAudioDelay>>

    /** Read on the playback thread, for the device playing now. */
    fun currentLatencyUs(): Long

    fun set(milliseconds: Int, device: AudioDevice)

    fun remove(id: String)

    companion object {
        const val MAX_MS = 500
        const val STEP_MS = 10
    }
}

class SystemAudioOutputDelay(
    private val audioManager: AudioManager,
    private val preferences: SharedPreferences,
) : AudioOutputDelay {
    private val _currentDevice = MutableStateFlow<AudioDevice?>(null)
    override val currentDevice: StateFlow<AudioDevice?> = _currentDevice.asStateFlow()

    private val _saved = MutableStateFlow(load())
    override val saved: StateFlow<Map<String, SavedAudioDelay>> = _saved.asStateFlow()

    @Volatile private var currentUs = 0L

    init {
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()

                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
            },
            Handler(Looper.getMainLooper()),
        )
        refresh()
    }

    override fun currentLatencyUs(): Long = currentUs

    override fun set(milliseconds: Int, device: AudioDevice) {
        val clamped = milliseconds.coerceIn(0, AudioOutputDelay.MAX_MS)
        update(_saved.value + (device.id to SavedAudioDelay(device.name, clamped)))
    }

    override fun remove(id: String) = update(_saved.value - id)

    private fun update(value: Map<String, SavedAudioDelay>) {
        _saved.value = value
        preferences.edit().putString(KEY, encode(value)).apply()
        applyCurrent()
    }

    private fun refresh() {
        // With Bluetooth media connected, Android sends media there, so its presence is enough.
        val output =
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type in WIRELESS_TYPES }
        _currentDevice.value =
            output?.let {
                val name = it.productName?.toString().orEmpty()
                AudioDevice(id = it.address.ifBlank { name }, name = name)
            }
        applyCurrent()
    }

    private fun applyCurrent() {
        val ms = _currentDevice.value?.let { _saved.value[it.id]?.milliseconds } ?: 0
        currentUs = ms * US_PER_MS
    }

    private fun load(): Map<String, SavedAudioDelay> {
        val json = preferences.getString(KEY, null) ?: return emptyMap()
        val root = JSONObject(json)
        return root.keys().asSequence().associateWith { id ->
            val entry = root.getJSONObject(id)
            SavedAudioDelay(entry.getString("name"), entry.getInt("ms"))
        }
    }

    private fun encode(value: Map<String, SavedAudioDelay>): String = JSONObject().apply {
        value.forEach { (id, saved) ->
            put(id, JSONObject().put("name", saved.name).put("ms", saved.milliseconds))
        }
    }.toString()

    private companion object {
        const val KEY = "devices"
        const val US_PER_MS = 1_000L
        val WIRELESS_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
            )
    }
}
