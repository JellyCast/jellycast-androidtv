package nl.jellycast.tv.core.playback.mkv

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.mkv.EbmlProcessor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser

private const val ID_CLUSTER = 0x1F43B675
private const val ID_TRACK_ENTRY = 0xAE
private const val ID_TRACK_NUMBER = 0xD7
private const val ID_TRACK_TYPE = 0x83
private const val ID_CUES = 0x1C53BB6B
private const val ID_CUE_POINT = 0xBB
private const val ID_CUE_TIME = 0xB3
private const val ID_CUE_TRACK_POSITIONS = 0xB7
private const val ID_CUE_TRACK = 0xF7
private const val ID_CUE_CLUSTER_POSITION = 0xF1
private const val ID_CUE_RELATIVE_POSITION = 0xF0

private const val ID_CHAPTERS = 0x1043A770

private const val TRACK_TYPE_SUBTITLE = 0x11L

/**
 * Media3 reads the seek index (`Cues`) element by element and keeps cues for every track; files
 * with dozens of subtitle tracks have a multi-megabyte index that delays the first frame by
 * seconds. This reads it as one block, keeps only video and audio points and replays them through
 * the regular `protected` methods, so Media3 builds the same seek map minus subtitle points.
 */
@OptIn(UnstableApi::class)
class FastCuesMatroskaExtractor(subtitleParserFactory: SubtitleParser.Factory, flags: Int) :
    MatroskaExtractor(subtitleParserFactory, flags) {
    val clusters = ClusterTracker()
    private val trackTypes = HashMap<Long, Long>()
    private var currentTrackNumber = -1L
    private var currentTrackType = -1L
    private var cuesHandled = false

    /**
     * Chapters are skipped: unused, and Media3 1.11 crashes on nested chapters (media#3438, fixed
     * after 1.11.1). Remove that case after the next Media3 update.
     */
    override fun getElementType(id: Int): Int = when (id) {
        ID_CUES -> EbmlProcessor.ELEMENT_TYPE_BINARY
        ID_CHAPTERS -> EbmlProcessor.ELEMENT_TYPE_UNKNOWN
        else -> super.getElementType(id)
    }

    override fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long) {
        super.startMasterElement(id, contentPosition, contentSize)
        when (id) {
            ID_TRACK_ENTRY -> {
                currentTrackNumber = -1
                currentTrackType = -1
            }

            ID_CLUSTER -> clusters.onClusterStart(contentPosition, contentSize)
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        super.seek(position, timeUs)
        clusters.reset()
    }

    override fun endMasterElement(id: Int) {
        if (id == ID_TRACK_ENTRY &&
            currentTrackNumber >= 0
        ) {
            trackTypes[currentTrackNumber] = currentTrackType
        }
        super.endMasterElement(id)
    }

    override fun integerElement(id: Int, value: Long) {
        super.integerElement(id, value)
        when (id) {
            ID_TRACK_NUMBER -> currentTrackNumber = value
            ID_TRACK_TYPE -> currentTrackType = value
        }
    }

    override fun binaryElement(id: Int, contentSize: Int, input: ExtractorInput) {
        if (id != ID_CUES) {
            super.binaryElement(id, contentSize, input)
            return
        }
        // The second copy at the end of the file, after the clusters.
        if (cuesHandled) {
            input.skipFully(contentSize)
            return
        }
        val contentPosition = input.position
        val bytes = ByteArray(contentSize)
        input.readFully(bytes, 0, contentSize)
        cuesHandled = true

        val withoutSubtitles = parseMatroskaCues(bytes) { trackTypes[it] != TRACK_TYPE_SUBTITLE }
        // An odd file without video or audio points keeps everything, as Media3 would.
        val cues = withoutSubtitles.ifEmpty { parseMatroskaCues(bytes) { true } }
        replayCues(cues, contentPosition, contentSize.toLong())
    }

    private fun replayCues(cues: List<CuePoint>, contentPosition: Long, contentSize: Long) {
        super.startMasterElement(ID_CUES, contentPosition, contentSize)
        for (cue in cues) {
            super.startMasterElement(ID_CUE_POINT, contentPosition, 0)
            super.integerElement(ID_CUE_TIME, cue.time)
            for (position in cue.positions) {
                super.startMasterElement(ID_CUE_TRACK_POSITIONS, contentPosition, 0)
                super.integerElement(ID_CUE_TRACK, position.track)
                super.integerElement(ID_CUE_CLUSTER_POSITION, position.clusterPosition)
                position.relativePosition?.let {
                    super.integerElement(ID_CUE_RELATIVE_POSITION, it)
                }
                super.endMasterElement(ID_CUE_TRACK_POSITIONS)
            }
            super.endMasterElement(ID_CUE_POINT)
        }
        super.endMasterElement(ID_CUES)
    }
}
