package nl.jellycast.core.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.mediacodec.ForwardingMediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Dolby Vision optionally plays through a plain HEVC decoder (the HDR10 base layer), video uses
 * hardware only, and Dolby surround avoids generic platform decoders that downmix to stereo.
 */
@OptIn(UnstableApi::class)
internal class JellyCastCodecSelector(private val decodeDolbyVisionAsHevc: Boolean) :
    MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val queryMime =
            if (decodeDolbyVisionAsHevc && mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                MimeTypes.VIDEO_H265
            } else {
                mimeType
            }
        val decoders =
            MediaCodecSelector.DEFAULT.getDecoderInfos(
                queryMime,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
        if (MimeTypes.isVideo(mimeType)) {
            return withoutSoftwareVideoFallback(decoders) {
                it.softwareOnly
            }
        }
        if (!MimeTypes.isAudio(mimeType)) return decoders
        val ffmpeg = FfmpegLibrary.supportsFormat(mimeType)
        return decoders.filter { allowPlatformAudioDecoder(mimeType, it.name, ffmpeg) }
    }
}

/**
 * Limits passthrough to [passthroughAudio] (`null` = whatever the output supports); a refused
 * format is decoded (usually by FFmpeg) to PCM, as the playback plan told the server.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
internal class JellyCastRenderersFactory(
    context: Context,
    private val passthroughAudio: Set<String>?,
    private val frameStats: FrameStats,
) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val renderer = TextRenderer(output, outputLooper)
        renderer.experimentalSetLegacyDecodingEnabled(true)
        out += renderer
    }

    /**
     * Media3's video renderer with [FramePacer] on each frame's release time. Skipping frames
     * with an identical release time is off, because the pacer can show such a frame on the next
     * vsync.
     */
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out,
        )
        val index = out.indexOfFirst { it is MediaCodecVideoRenderer }
        if (index < 0) return
        out[index] =
            PacedVideoRenderer(
                MediaCodecVideoRenderer
                    .Builder(context)
                    .setCodecAdapterFactory(
                        PacedCodecAdapterFactory(codecAdapterFactory, context, frameStats),
                    )
                    .setMediaCodecSelector(mediaCodecSelector)
                    .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                    .setEnableDecoderFallback(enableDecoderFallback)
                    .setEventHandler(eventHandler)
                    .setEventListener(eventListener)
                    .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
                    .apply {
                        // Pinned off: untested whether Amlogic and MediaTek decoders skip
                        // decode-only buffers correctly, so a Media3 update can't silently change
                        // seeking. The flag only exists from Android 14.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            experimentalSetEnableMediaCodecBufferDecodeOnlyFlag(false)
                        }
                    },
            )
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink? {
        val sink = super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
        val allowed = passthroughAudio ?: return sink
        return sink?.let { PassthroughPolicyAudioSink(it, allowed) }
    }
}

@OptIn(UnstableApi::class)
private class PacedVideoRenderer(builder: MediaCodecVideoRenderer.Builder) :
    MediaCodecVideoRenderer(builder) {
    override fun shouldSkipBuffersWithIdenticalReleaseTime(): Boolean = false
}

@OptIn(UnstableApi::class)
private class PacedCodecAdapterFactory(
    private val delegate: MediaCodecAdapter.Factory,
    private val context: Context,
    private val frameStats: FrameStats,
) : MediaCodecAdapter.Factory {
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter =
        PacedCodecAdapter(
            delegate.createAdapter(configuration),
            // Not `context.display`: the application context has no display and throws.
            context.getSystemService(
                DisplayManager::class.java,
            )?.getDisplay(Display.DEFAULT_DISPLAY),
            frameStats,
        )
}

/**
 * Applies [FramePacer] on release and counts frames that actually reached the screen. Forwarding
 * instead of Kotlin delegation: delegation skips the interface default methods that the
 * asynchronous adapter overrides.
 */
@OptIn(UnstableApi::class)
private class PacedCodecAdapter(
    inner: MediaCodecAdapter,
    private val display: Display?,
    private val frameStats: FrameStats,
) : ForwardingMediaCodecAdapter(inner) {
    private val pacer = FramePacer()
    private var vsyncNs = 0L
    private var vsyncReadAtMs = Long.MIN_VALUE

    init {
        // Android reports each shown frame; a frame the compositor dropped is not reported.
        inner.setOnFrameRenderedListener(
            { _, _, _ -> frameStats.onRendered() },
            Handler(Looper.getMainLooper()),
        )
    }

    /** Media3 only sets this when tunneling; then Android does not count per frame. */
    override fun setOnFrameRenderedListener(
        listener: MediaCodecAdapter.OnFrameRenderedListener,
        handler: Handler,
    ) {
        frameStats.markTunneled()
        super.setOnFrameRenderedListener(listener, handler)
    }

    override fun releaseOutputBuffer(index: Int, renderTimeStampNs: Long) {
        frameStats.onReleased()
        super.releaseOutputBuffer(index, pacer.pace(renderTimeStampNs, currentVsyncNs()))
    }

    override fun releaseOutputBuffer(index: Int, render: Boolean) {
        if (render) frameStats.onReleased()
        super.releaseOutputBuffer(index, render)
    }

    // Re-read periodically: the display mode switches just before or during playback.
    private fun currentVsyncNs(): Long {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - vsyncReadAtMs >= VSYNC_REREAD_MS) {
            vsyncReadAtMs = nowMs
            val hz = display?.refreshRate ?: 0f
            vsyncNs = if (hz > 1f) (NANOS_PER_SECOND / hz).toLong() else 0L
        }
        return vsyncNs
    }

    private companion object {
        const val VSYNC_REREAD_MS = 1_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

/** Refuses bitstreams of codecs outside [allowed]; PCM always passes. */
@OptIn(UnstableApi::class)
private class PassthroughPolicyAudioSink(sink: AudioSink, private val allowed: Set<String>) :
    ForwardingAudioSink(sink) {
    private fun refused(format: Format): Boolean =
        passthroughCodecFor(format.sampleMimeType)?.let { it !in allowed } ?: false

    override fun supportsFormat(format: Format): Boolean =
        !refused(format) && super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (refused(format)) AudioSink.SINK_FORMAT_UNSUPPORTED else super.getFormatSupport(format)
}
