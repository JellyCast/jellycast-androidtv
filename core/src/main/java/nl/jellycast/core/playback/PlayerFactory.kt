package nl.jellycast.core.playback

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.playback.mkv.JellyCastExtractorsFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder

private const val SEEK_INCREMENT_MS = 10_000L
private const val HTTP_CONNECT_TIMEOUT_MS = 15_000
private const val HTTP_READ_TIMEOUT_MS = 30_000
private const val SMALL_HEAP_BYTES = 192L * 1024 * 1024

/** One player per player screen; never keep it in a singleton, the hardware decoder is scarce. */
@OptIn(UnstableApi::class)
class PlayerFactory(
    private val context: Context,
    private val api: ApiClient,
    private val enableEventLogging: Boolean,
) {
    fun create(
        plan: PlaybackPlan,
        tunneling: Boolean = false,
        frameStats: FrameStats = FrameStats(),
    ): ExoPlayer {
        val renderers =
            JellyCastRenderersFactory(
                context,
                passthroughAudio = plan.passthroughAudio,
                frameStats = frameStats,
            )
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
                .setMediaCodecSelector(
                    JellyCastCodecSelector(decodeDolbyVisionAsHevc = plan.decodeDolbyVisionAsHevc),
                )

        // When the audio output changes mid-playback (Bluetooth on, amplifier off), ExoPlayer
        // re-selects, e.g. from passthrough to decoding locally.
        val trackSelector =
            DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                        .setTunnelingEnabled(tunneling),
                )
            }

        // The token goes in a header, not the URL, so it never ends up in a log. Generous
        // timeouts: when transcoding, the server first waits for the disk and ffmpeg.
        val http =
            DefaultHttpDataSource
                .Factory()
                .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
                .setUserAgent("JellyCast/${api.clientInfo.version}")
                .setDefaultRequestProperties(mapOf("Authorization" to authorizationHeader()))
                .apply { if (enableEventLogging) setTransferListener(ThroughputLogger()) }
        // By default Media3 decodes all subtitle tracks on the loading thread; with many PGS
        // tracks that stalls loading. Decode only the selected track, in the text renderer.
        val mediaSources =
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, http),
                JellyCastExtractorsFactory(),
            ).experimentalParseSubtitlesDuringExtraction(false)

        return ExoPlayer
            .Builder(context, renderers)
            .setMediaSourceFactory(mediaSources)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl())
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            ).setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            // Exact seeking in 4K makes the decoder chew through seconds of frames first.
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .also { player -> if (enableEventLogging) player.addAnalyticsListener(EventLogger()) }
    }

    private fun loadControl(): DefaultLoadControl {
        val defaultBytes =
            DefaultLoadControl.DEFAULT_VIDEO_BUFFER_SIZE +
                DefaultLoadControl.DEFAULT_AUDIO_BUFFER_SIZE
        val heapBytes = Runtime.getRuntime().maxMemory()
        return DefaultLoadControl
            .Builder()
            .setTargetBufferBytes(bufferBytesFor(heapBytes, defaultBytes))
            .build()
    }

    private fun authorizationHeader(): String = AuthorizationHeaderBuilder.buildHeader(
        clientName = api.clientInfo.name,
        clientVersion = api.clientInfo.version,
        deviceId = api.deviceInfo.id,
        deviceName = api.deviceInfo.name,
        accessToken = api.accessToken,
    )
}

fun shouldTunnel(plan: PlaybackPlan, mode: FormatSupport): Boolean =
    plan.allowTunneling && usesTunneling(mode, plan, Build.SOC_MANUFACTURER)

fun PlaybackPlan.toMediaItem(): MediaItem = MediaItem
    .Builder()
    .setMediaId(itemId)
    .setUri(streamUrl)
    .apply { if (playMethod != PlayMethod.DirectPlay) setMimeType(MimeTypes.APPLICATION_M3U8) }
    .build()

/**
 * The buffer lives on the Java heap, so it has to fit next to the UI, Coil and FFmpeg: half the
 * heap, a third on a small heap, never more than [defaultBytes].
 */
internal fun bufferBytesFor(heapBytes: Long, defaultBytes: Int): Int {
    val share = if (heapBytes <= SMALL_HEAP_BYTES) heapBytes / 3 else heapBytes / 2
    return minOf(share, defaultBytes.toLong()).toInt()
}
