package nl.jellycast.tv.core.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface PlaybackSettingsStore {
    val settings: StateFlow<PlaybackSettings>

    /** Use before a playback plan: right after app start [settings] still holds the defaults. */
    suspend fun current(): PlaybackSettings

    fun setFrameRateMode(mode: FrameRateMode)

    fun setFormatSupport(format: PlaybackFormat, support: FormatSupport)

    fun setDownmixToStereo(enabled: Boolean)

    fun setStartDelay(ms: Int)

    fun setDecodeLosslessAudioLocally(enabled: Boolean)

    fun setBurnInAss(enabled: Boolean)

    fun setMaxStreamingBitrate(bitsPerSecond: Int)

    fun setSubtitleSize(size: SubtitleSize)

    fun setSubtitleBackground(enabled: Boolean)

    fun setTunneling(mode: FormatSupport)

    fun setIgnoreVideoLevel(enabled: Boolean)
}

/** Writes run in the app [scope], so a menu choice is saved even when the screen closes at once. */
class DefaultPlaybackSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : PlaybackSettingsStore {
    override val settings: StateFlow<PlaybackSettings> =
        dataStore.data
            .map { it.toSettings() }
            .stateIn(scope, SharingStarted.Eagerly, PlaybackSettings())

    override suspend fun current(): PlaybackSettings = dataStore.data.first().toSettings()

    private fun Preferences.toSettings(): PlaybackSettings {
        val prefs = this
        val defaults = PlaybackSettings()
        return PlaybackSettings(
            decodeLosslessAudioLocally =
                prefs[DECODE_LOSSLESS] ?: defaults.decodeLosslessAudioLocally,
            frameRateMode =
                FrameRateMode.entries.firstOrNull { it.name == prefs[FRAME_RATE_MODE] }
                    // Legacy boolean from before the three-way setting: off stays off.
                    ?: if (prefs[MATCH_FRAME_RATE] ==
                        false
                    ) {
                        FrameRateMode.Off
                    } else {
                        defaults.frameRateMode
                    },
            formatSupport =
                PlaybackFormat.entries
                    .mapNotNull { format ->
                        FormatSupport.entries
                            .firstOrNull { it.name == prefs[format.key()] }
                            ?.let { format to it }
                    }.toMap(),
            downmixToStereo = prefs[DOWNMIX_TO_STEREO] ?: defaults.downmixToStereo,
            startDelayMs = prefs[START_DELAY_MS] ?: defaults.startDelayMs,
            burnInAss = prefs[BURN_IN_ASS] ?: defaults.burnInAss,
            maxStreamingBitrate = prefs[MAX_BITRATE] ?: defaults.maxStreamingBitrate,
            subtitleSize =
                SubtitleSize.entries.firstOrNull { it.name == prefs[SUBTITLE_SIZE] }
                    ?: defaults.subtitleSize,
            subtitleBackground = prefs[SUBTITLE_BACKGROUND] ?: defaults.subtitleBackground,
            tunneling =
                FormatSupport.entries.firstOrNull { it.name == prefs[TUNNELING] }
                    ?: defaults.tunneling,
            ignoreVideoLevel = prefs[IGNORE_VIDEO_LEVEL] ?: defaults.ignoreVideoLevel,
        )
    }

    override fun setFrameRateMode(mode: FrameRateMode) = save { it[FRAME_RATE_MODE] = mode.name }

    override fun setFormatSupport(format: PlaybackFormat, support: FormatSupport) = save {
        it[format.key()] = support.name
    }

    override fun setDownmixToStereo(enabled: Boolean) = save { it[DOWNMIX_TO_STEREO] = enabled }

    override fun setStartDelay(ms: Int) = save { it[START_DELAY_MS] = ms }

    override fun setDecodeLosslessAudioLocally(enabled: Boolean) = save {
        it[DECODE_LOSSLESS] =
            enabled
    }

    override fun setBurnInAss(enabled: Boolean) = save { it[BURN_IN_ASS] = enabled }

    override fun setMaxStreamingBitrate(bitsPerSecond: Int) = save {
        it[MAX_BITRATE] = bitsPerSecond
    }

    override fun setSubtitleSize(size: SubtitleSize) = save { it[SUBTITLE_SIZE] = size.name }

    override fun setSubtitleBackground(enabled: Boolean) = save {
        it[SUBTITLE_BACKGROUND] = enabled
    }

    override fun setTunneling(mode: FormatSupport) = save { it[TUNNELING] = mode.name }

    override fun setIgnoreVideoLevel(enabled: Boolean) = save { it[IGNORE_VIDEO_LEVEL] = enabled }

    private fun save(block: (MutablePreferences) -> Unit) {
        scope.launch { dataStore.edit(block) }
    }

    private companion object {
        val DECODE_LOSSLESS = booleanPreferencesKey("decode_lossless_audio_locally")
        val MATCH_FRAME_RATE = booleanPreferencesKey("match_frame_rate")
        val FRAME_RATE_MODE = stringPreferencesKey("frame_rate_mode")
        val DOWNMIX_TO_STEREO = booleanPreferencesKey("downmix_to_stereo")
        val START_DELAY_MS = intPreferencesKey("start_delay_ms")

        fun PlaybackFormat.key() = stringPreferencesKey("format_support_${name.lowercase()}")
        val BURN_IN_ASS = booleanPreferencesKey("burn_in_ass")
        val MAX_BITRATE = intPreferencesKey("max_streaming_bitrate")
        val SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        val SUBTITLE_BACKGROUND = booleanPreferencesKey("subtitle_background")
        val TUNNELING = stringPreferencesKey("tunneling_mode")
        val IGNORE_VIDEO_LEVEL = booleanPreferencesKey("ignore_video_level")
    }
}
