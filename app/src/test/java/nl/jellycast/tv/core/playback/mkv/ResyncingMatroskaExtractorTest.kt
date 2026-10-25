package nl.jellycast.tv.core.playback.mkv

import androidx.media3.common.DataReader
import androidx.media3.common.ParserException
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ResyncingMatroskaExtractorTest {
    /** Throws [error] on read and records seek targets. */
    private class ThrowingExtractor(private val error: Exception) : Extractor {
        val seeks = mutableListOf<Long>()

        override fun sniff(input: ExtractorInput) = true

        override fun init(output: ExtractorOutput) = Unit

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = throw error

        override fun seek(position: Long, timeUs: Long) {
            seeks += position
        }

        override fun release() = Unit
    }

    private val emptyReader = DataReader { _, _, _ -> 0 }

    private fun inputAt(position: Long) = DefaultExtractorInput(emptyReader, position, 1_000_000)

    private val clusters =
        ClusterTracker().apply {
            onClusterStart(contentPosition = 1_000, contentSize = 500)
        }

    @Test
    fun `corrupt data in a cluster continues at the next cluster`() {
        // How Media3 reports a zero byte where an element ID belongs.
        val inner = ThrowingExtractor(IllegalStateException("No valid varint length mask found"))
        val extractor = ResyncingMatroskaExtractor(inner, clusters)
        val seekPosition = PositionHolder()

        val result = extractor.read(inputAt(1_200), seekPosition)

        assertEquals(Extractor.RESULT_SEEK, result)
        assertEquals(1_500L, seekPosition.position)
        assertEquals(listOf(1_500L), inner.seeks)
    }

    @Test
    fun `a parser error in a cluster also continues at the next cluster`() {
        val inner = ThrowingExtractor(ParserException.createForMalformedContainer("corrupt", null))
        val seekPosition = PositionHolder()

        val result = ResyncingMatroskaExtractor(inner, clusters).read(inputAt(1_499), seekPosition)

        assertEquals(Extractor.RESULT_SEEK, result)
        assertEquals(1_500L, seekPosition.position)
    }

    @Test
    fun `an error outside a cluster stays an error`() {
        val error = IllegalStateException("No valid varint length mask found")
        val extractor = ResyncingMatroskaExtractor(ThrowingExtractor(error), clusters)

        assertThrowsSame(error) { extractor.read(inputAt(1_500), PositionHolder()) }
        assertThrowsSame(error) { extractor.read(inputAt(999), PositionHolder()) }
    }

    @Test
    fun `a network error is never skipped`() {
        val error = IOException("connection lost")
        val extractor = ResyncingMatroskaExtractor(ThrowingExtractor(error), clusters)

        assertThrowsSame(error) { extractor.read(inputAt(1_200), PositionHolder()) }
    }

    @Test
    fun `after a seek the cluster is unknown until the next one starts`() {
        val tracker = ClusterTracker()
        tracker.onClusterStart(contentPosition = 1_000, contentSize = 500)
        tracker.reset()

        assertEquals(null, tracker.endAt(1_200))
    }

    @Test
    fun `a cluster of unknown size has no end`() {
        val tracker = ClusterTracker()
        // EBML "unknown size" (8 bytes, all ones), as live recordings write it.
        tracker.onClusterStart(contentPosition = 1_000, contentSize = (1L shl 56) - 1)

        assertEquals(null, tracker.endAt(1_200))
    }

    private fun assertThrowsSame(expected: Exception, block: () -> Unit) {
        try {
            block()
            fail("Expected ${expected.message}")
        } catch (e: Exception) {
            assertSame(expected, e)
        }
    }
}
