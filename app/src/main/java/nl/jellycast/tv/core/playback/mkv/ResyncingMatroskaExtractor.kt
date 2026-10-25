package nl.jellycast.tv.core.playback.mkv

import androidx.annotation.OptIn
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SniffFailure
import timber.log.Timber

/** EBML "unknown size" in 8 bytes: all data bits set. */
private const val UNKNOWN_SIZE = (1L shl 56) - 1

class ClusterTracker {
    private var start = -1L
    private var end = -1L

    fun onClusterStart(contentPosition: Long, contentSize: Long) {
        val known = contentSize in 0 until UNKNOWN_SIZE
        start = if (known) contentPosition else -1
        end = if (known) contentPosition + contentSize else -1
    }

    /** After a seek the cluster is unknown until the next one starts. */
    fun reset() {
        start = -1
        end = -1
    }

    /** Start of the next cluster when [position] lies in the current one. */
    fun endAt(position: Long): Long? = end.takeIf { start >= 0 && position in start until end }
}

/**
 * Continues at the next cluster after corrupt data inside a cluster, like ffmpeg (and so the
 * server) does, instead of failing over to a remux. Only content errors inside a cluster of known
 * size are caught; network errors and errors in the header or index stay errors.
 */
@OptIn(UnstableApi::class)
class ResyncingMatroskaExtractor(
    private val delegate: Extractor,
    private val clusters: ClusterTracker,
) : Extractor {
    constructor(extractor: FastCuesMatroskaExtractor) : this(extractor, extractor.clusters)

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun getSniffFailureDetails(): List<SniffFailure> = delegate.sniffFailureDetails

    override fun init(output: ExtractorOutput) = delegate.init(output)

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = try {
        delegate.read(input, seekPosition)
    } catch (e: ParserException) {
        skipToNextCluster(input, seekPosition, e)
    } catch (e: IllegalStateException) {
        skipToNextCluster(input, seekPosition, e)
    }

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation

    private fun skipToNextCluster(
        input: ExtractorInput,
        seekPosition: PositionHolder,
        error: Exception,
    ): Int {
        val position = input.position
        val next = clusters.endAt(position) ?: throw error
        Timber.w(
            "Corrupt MKV data at byte %d (%s); continuing at byte %d",
            position,
            error.message,
            next,
        )
        delegate.seek(next, 0)
        seekPosition.position = next
        return Extractor.RESULT_SEEK
    }
}
