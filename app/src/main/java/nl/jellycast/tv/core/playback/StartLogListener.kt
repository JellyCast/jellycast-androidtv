package nl.jellycast.tv.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink

/** Only start events and errors; never a line per frame. */
@OptIn(UnstableApi::class)
class StartLogListener(private val log: PlaybackStartLog) : AnalyticsListener {
    private var playingLogged = false
    private var droppedFrames = 0

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) = log.log("Video decoder open: $decoderName ($initializationDurationMs ms)")

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        val color = format.colorInfo?.toLogString().orEmpty()
        log.log(
            "Video format: ${format.codecs ?: format.sampleMimeType} " +
                "${format.width}x${format.height} ${format.frameRate}fps $color",
        )
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long,
    ) = log.log("First frame at position ${eventTime.currentPlaybackPositionMs} ms")

    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig,
    ) = log.log(
        "Audio output: encoding ${audioTrackConfig.encoding}, " +
            "${Integer.bitCount(audioTrackConfig.channelConfig)}ch" +
            if (audioTrackConfig.tunneling) ", tunneling" else "",
    )

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        if (isPlaying && !playingLogged) {
            playingLogged = true
            log.log("Playing from ${eventTime.currentPlaybackPositionMs} ms")
        }
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        if (state == Player.STATE_BUFFERING && playingLogged) {
            log.log("Buffering at ${eventTime.currentPlaybackPositionMs} ms")
        }
    }

    override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, error: Exception) =
        log.log("Video decoder error: ${error.javaClass.simpleName}: ${error.message}")

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) =
        log.log("Playback error: ${error.errorCodeName}")

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) {
        this.droppedFrames += droppedFrames
    }

    override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) = log.log(
        "Stopped at ${eventTime.currentPlaybackPositionMs} ms, $droppedFrames dropped frames",
    )
}
