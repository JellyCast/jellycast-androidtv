package nl.jellycast.tv.core.playback

import androidx.media3.common.PlaybackException
import nl.jellycast.tv.core.model.PlayMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryTest {
    private val start = PlaybackFallback()

    @Test
    fun `errors are classified by what a fallback can fix`() {
        fun classify(
            code: Int,
            source: Boolean = false,
            renderer: Boolean = false,
            mime: String? = null,
        ) = classifyPlaybackFailure(code, source, renderer, mime)

        assertEquals(
            PlaybackFailure.Network,
            classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, source = true),
        )
        assertEquals(
            PlaybackFailure.Source,
            classify(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, source = true),
        )
        assertEquals(
            PlaybackFailure.Audio,
            classify(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, renderer = true),
        )
        assertEquals(
            PlaybackFailure.Audio,
            classify(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                renderer = true,
                mime = "audio/eac3",
            ),
        )
        assertEquals(
            PlaybackFailure.Video,
            classify(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                renderer = true,
                mime = "video/hevc",
            ),
        )
        assertEquals(PlaybackFailure.Other, classify(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }

    @Test
    fun `a source error remuxes, then transcodes, then gives up`() {
        val remux = recoverFrom(PlaybackFailure.Source, start, PlayMethod.DirectPlay, false)
        val afterRemux = PlaybackFallback(directPlay = false)
        assertEquals(PlaybackRecovery.Fallback(afterRemux), remux)

        val transcode =
            recoverFrom(PlaybackFailure.Source, afterRemux, PlayMethod.DirectStream, false)
        val afterTranscode = PlaybackFallback(directPlay = false, videoCopy = false)
        assertEquals(PlaybackRecovery.Fallback(afterTranscode), transcode)

        assertEquals(
            PlaybackRecovery.GiveUp,
            recoverFrom(PlaybackFailure.Source, afterTranscode, PlayMethod.Transcode, false),
        )
    }

    @Test
    fun `an audio error decodes locally, then asks for server stereo, then gives up`() {
        val noPassthrough = PlaybackFallback(passthrough = false)
        assertEquals(
            PlaybackRecovery.Fallback(noPassthrough),
            recoverFrom(PlaybackFailure.Audio, start, PlayMethod.DirectPlay, false),
        )
        val stereo = noPassthrough.copy(stereo = true)
        assertEquals(
            PlaybackRecovery.Fallback(stereo),
            recoverFrom(PlaybackFailure.Audio, noPassthrough, PlayMethod.DirectPlay, false),
        )
        assertEquals(
            PlaybackRecovery.GiveUp,
            recoverFrom(PlaybackFailure.Audio, stereo, PlayMethod.DirectStream, false),
        )
    }

    @Test
    fun `a video error retries, then transcodes`() {
        assertEquals(
            PlaybackRecovery.Retry,
            recoverFrom(PlaybackFailure.Video, start, PlayMethod.DirectPlay, retryUsed = false),
        )
        assertEquals(
            PlaybackRecovery.Fallback(PlaybackFallback(directPlay = false, videoCopy = false)),
            recoverFrom(PlaybackFailure.Video, start, PlayMethod.DirectPlay, retryUsed = true),
        )
    }

    @Test
    fun `a network error retries once and never remuxes`() {
        assertEquals(
            PlaybackRecovery.Retry,
            recoverFrom(PlaybackFailure.Network, start, PlayMethod.DirectPlay, retryUsed = false),
        )
        assertEquals(
            PlaybackRecovery.GiveUp,
            recoverFrom(PlaybackFailure.Network, start, PlayMethod.DirectPlay, retryUsed = true),
        )
    }

    @Test
    fun `without passthrough or with stereo no bitstream goes to the amplifier`() {
        assertEquals(ChromecastCapabilities, ChromecastCapabilities.limitedBy(start))
        assertEquals(
            emptySet<String>(),
            ChromecastCapabilities.limitedBy(
                PlaybackFallback(passthrough = false),
            ).passthroughAudio,
        )
        assertEquals(
            emptySet<String>(),
            ChromecastCapabilities.limitedBy(PlaybackFallback(stereo = true)).passthroughAudio,
        )
    }

    @Test
    fun `a tunneled plan first drops tunneling after an audio or video error`() {
        val start = PlaybackFallback()
        val withoutTunneling = PlaybackFallback(tunneling = false)
        for (failure in listOf(
            PlaybackFailure.Audio,
            PlaybackFailure.Video,
            PlaybackFailure.Other,
        )) {
            assertEquals(
                PlaybackRecovery.Fallback(withoutTunneling),
                recoverFrom(failure, start, PlayMethod.DirectPlay, false, tunneled = true),
            )
        }
        // Then the regular steps.
        assertEquals(
            PlaybackRecovery.Fallback(withoutTunneling.copy(passthrough = false)),
            recoverFrom(
                PlaybackFailure.Audio,
                withoutTunneling,
                PlayMethod.DirectPlay,
                false,
                tunneled = false,
            ),
        )
    }

    @Test
    fun `network and source errors keep tunneling`() {
        assertEquals(
            PlaybackRecovery.Retry,
            recoverFrom(
                PlaybackFailure.Network,
                PlaybackFallback(),
                PlayMethod.DirectPlay,
                false,
                true,
            ),
        )
        assertEquals(
            PlaybackRecovery.Fallback(PlaybackFallback(directPlay = false)),
            recoverFrom(
                PlaybackFailure.Source,
                PlaybackFallback(),
                PlayMethod.DirectPlay,
                false,
                true,
            ),
        )
    }

    @Test
    fun `slow loading steps down to 40, 20 and 10 Mbps, then gives up`() {
        var fallback = PlaybackFallback()
        for (expected in listOf(40_000_000, 20_000_000, 10_000_000)) {
            val recovery =
                recoverFrom(PlaybackFailure.TooSlow, fallback, PlayMethod.DirectPlay, false)
            assertEquals(PlaybackRecovery.Fallback(fallback.copy(maxBitrate = expected)), recovery)
            fallback = (recovery as PlaybackRecovery.Fallback).fallback
        }
        assertEquals(
            PlaybackRecovery.GiveUp,
            recoverFrom(PlaybackFailure.TooSlow, fallback, PlayMethod.Transcode, false),
        )
    }
}
