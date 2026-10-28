package nl.jellycast.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/** What actually happens during playback: decoder, format, passthrough or PCM. */
data class DiagnosticsState(
    val videoDecoder: String? = null,
    val videoFormat: String? = null,
    val audioDecoder: String? = null,
    val audioTrack: String? = null,
    val audioOutput: String? = null,
    val droppedFrames: Int = 0,
)

@OptIn(UnstableApi::class)
class PlaybackDiagnostics : AnalyticsListener {
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        Timber.i("Video decoder: %s", decoderName)
        _state.update { it.copy(videoDecoder = decoderName) }
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        val size = "${format.width}x${format.height}"
        val color = format.colorInfo?.toLogString().orEmpty()
        val codec = "${format.sampleMimeType} ${format.codecs.orEmpty()}"
        val description = "$codec $size ${format.frameRate}fps $color"
        Timber.i("Video format: %s", description)
        _state.update { it.copy(videoFormat = description.trim()) }
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        Timber.i("Audio decoder: %s", decoderName)
        _state.update { it.copy(audioDecoder = decoderName) }
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        val track = listOfNotNull(
            format.label,
            format.sampleMimeType,
            "${format.channelCount}ch",
        ).joinToString(" · ")
        Timber.i("Audio track in player: %s", track)
        _state.update { it.copy(audioTrack = track) }
    }

    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig,
    ) {
        val channels = audioTrackConfig.channelConfig.channelCount()
        val output = "${audioTrackConfig.encoding.encodingName()} ${channels}ch"
        Timber.i("Audio output: %s", output)
        _state.update { it.copy(audioOutput = output) }
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        Timber.d(
            "Load: position %s, %s kB in %s ms (type %s)",
            loadEventInfo.dataSpec.position,
            loadEventInfo.bytesLoaded / 1024,
            loadEventInfo.loadDurationMs,
            mediaLoadData.dataType,
        )
    }

    override fun onTracksChanged(
        eventTime: AnalyticsListener.EventTime,
        tracks: androidx.media3.common.Tracks,
    ) {
        Timber.d("Tracks known: %s groups", tracks.groups.size)
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) {
        _state.update { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
    }
}

private fun Int.encodingName(): String = when (this) {
    C.ENCODING_AC3 -> "passthrough AC3"
    C.ENCODING_E_AC3 -> "passthrough EAC3"
    C.ENCODING_E_AC3_JOC -> "passthrough EAC3-JOC (Atmos)"
    C.ENCODING_DTS -> "passthrough DTS"
    C.ENCODING_DTS_HD -> "passthrough DTS-HD"
    C.ENCODING_DOLBY_TRUEHD -> "passthrough TrueHD"
    C.ENCODING_PCM_16BIT -> "PCM 16-bit"
    C.ENCODING_PCM_FLOAT -> "PCM float"
    else -> "encoding $this"
}

private fun Int.channelCount(): Int = Integer.bitCount(this)
