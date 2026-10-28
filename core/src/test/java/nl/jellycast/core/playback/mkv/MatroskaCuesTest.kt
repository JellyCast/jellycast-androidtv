package nl.jellycast.core.playback.mkv

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatroskaCuesTest {
    private fun element(id: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val idBytes = generateSequence(id) {
            it ushr 8
        }.takeWhile { it != 0 }.map { it.toByte() }.toList().reversed()
        idBytes.forEach { out.write(it.toInt()) }
        // Size as a 4-byte vint (0x10 xx xx xx), as mkvmerge often writes it.
        out.write(0x10 or (content.size ushr 24))
        out.write(content.size ushr 16)
        out.write(content.size ushr 8)
        out.write(content.size)
        out.write(content)
        return out.toByteArray()
    }

    private fun uint(id: Int, value: Long): ByteArray {
        val bytes = (7 downTo 0).map {
            (value ushr (it * 8)).toByte()
        }.dropWhile { it == 0.toByte() }.ifEmpty { listOf(0.toByte()) }
        return element(id, bytes.toByteArray())
    }

    private fun trackPosition(track: Long, cluster: Long, relative: Long? = null) = element(
        0xB7,
        uint(0xF7, track) + uint(0xF1, cluster) + (
            relative?.let {
                uint(0xF0, it)
            } ?: ByteArray(0)
            ),
    )

    private fun cuePoint(time: Long, vararg positions: ByteArray) =
        element(0xBB, uint(0xB3, time) + positions.fold(ByteArray(0)) { acc, b -> acc + b })

    private val cues =
        cuePoint(0, trackPosition(1, 1_000), trackPosition(3, 1_000, relative = 42)) +
            cuePoint(5_000, trackPosition(1, 900_000)) +
            cuePoint(6_000, trackPosition(3, 950_000))

    @Test
    fun `reads times and positions`() {
        val result = parseMatroskaCues(cues) { true }
        assertEquals(listOf(0L, 5_000L, 6_000L), result.map { it.time })
        assertEquals(CueTrackPosition(3, 1_000, 42), result[0].positions[1])
        assertEquals(900_000L, result[1].positions.single().clusterPosition)
    }

    @Test
    fun `drops subtitle tracks and cues left empty`() {
        val result = parseMatroskaCues(cues) { it != 3L }
        assertEquals(listOf(0L, 5_000L), result.map { it.time })
        assertTrue(result.all { cue -> cue.positions.all { it.track == 1L } })
    }

    @Test
    fun `handles large cluster positions`() {
        val result = parseMatroskaCues(cuePoint(1, trackPosition(1, 102_675_063_919L))) { true }
        assertEquals(102_675_063_919L, result.single().positions.single().clusterPosition)
    }

    @Test
    fun `skips unknown elements`() {
        val withExtras = element(
            0xBB,
            uint(0xB3, 7) + uint(0xB2, 99) + trackPosition(1, 5) + element(0xEA, byteArrayOf(1, 2)),
        )
        val result = parseMatroskaCues(withExtras) { true }
        assertEquals(CuePoint(7, listOf(CueTrackPosition(1, 5, null))), result.single())
    }

    @Test
    fun `truncated data returns the intact part without crashing`() {
        val truncated = cues.copyOf(cues.size - 5)
        val result = parseMatroskaCues(truncated) { true }
        assertEquals(listOf(0L, 5_000L), result.map { it.time })
        assertEquals(emptyList<CuePoint>(), parseMatroskaCues(byteArrayOf(0, 0, 0)) { true })
    }
}
