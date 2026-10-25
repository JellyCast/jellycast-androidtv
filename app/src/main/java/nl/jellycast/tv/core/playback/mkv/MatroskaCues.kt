package nl.jellycast.tv.core.playback.mkv

/** EBML `CueTrackPositions`. */
data class CueTrackPosition(
    val track: Long,
    val clusterPosition: Long,
    val relativePosition: Long?,
)

/** EBML `CuePoint`; [time] in the file's timecode units. */
data class CuePoint(val time: Long, val positions: List<CueTrackPosition>)

private const val ID_CUE_POINT = 0xBB
private const val ID_CUE_TIME = 0xB3
private const val ID_CUE_TRACK_POSITIONS = 0xB7
private const val ID_CUE_TRACK = 0xF7
private const val ID_CUE_CLUSTER_POSITION = 0xF1
private const val ID_CUE_RELATIVE_POSITION = 0xF0

/**
 * Parses the body of a Matroska `Cues` element (without ID and size) in one pass. Cues without a
 * kept track are dropped; unknown elements are skipped.
 */
fun parseMatroskaCues(bytes: ByteArray, keepTrack: (Long) -> Boolean): List<CuePoint> {
    val reader = EbmlBytes(bytes)
    val cues = ArrayList<CuePoint>()
    while (reader.hasMore()) {
        if (!reader.header()) break
        val end = reader.position + reader.size
        if (reader.id == ID_CUE_POINT) reader.cuePoint(end, keepTrack)?.let(cues::add)
        reader.position = end
    }
    return cues
}

private fun EbmlBytes.cuePoint(end: Int, keepTrack: (Long) -> Boolean): CuePoint? {
    var time = -1L
    val positions = ArrayList<CueTrackPosition>(1)
    while (position < end) {
        if (!header()) return null
        val childEnd = position + size
        when (id) {
            ID_CUE_TIME -> time = uint(size)

            ID_CUE_TRACK_POSITIONS -> trackPosition(childEnd)?.takeIf {
                keepTrack(it.track)
            }?.let(positions::add)
        }
        position = childEnd
    }
    return if (time >= 0 && positions.isNotEmpty()) CuePoint(time, positions) else null
}

private fun EbmlBytes.trackPosition(end: Int): CueTrackPosition? {
    var track = -1L
    var cluster = -1L
    var relative: Long? = null
    while (position < end) {
        if (!header()) return null
        val childEnd = position + size
        when (id) {
            ID_CUE_TRACK -> track = uint(size)
            ID_CUE_CLUSTER_POSITION -> cluster = uint(size)
            ID_CUE_RELATIVE_POSITION -> relative = uint(size)
        }
        position = childEnd
    }
    return if (track >= 0 && cluster >= 0) CueTrackPosition(track, cluster, relative) else null
}

/** ID and size live in fields: a movie has hundreds of thousands of elements, so no object each. */
private class EbmlBytes(private val data: ByteArray) {
    var position = 0
    var id = 0
        private set
    var size = 0
        private set

    fun hasMore() = position < data.size

    /** `false` for a corrupt or truncated element. */
    fun header(): Boolean {
        val rawId = vint(keepMarker = true)
        if (rawId < 0) return false
        val rawSize = vint(keepMarker = false)
        if (rawSize < 0 || rawSize > data.size - position) return false
        id = rawId.toInt()
        size = rawSize.toInt()
        return true
    }

    fun uint(size: Int): Long {
        var value = 0L
        repeat(size) { value = (value shl 8) or (data[position + it].toLong() and 0xFF) }
        return value
    }

    /** -1 for corrupt or truncated data; not `Long?`, to avoid boxing. */
    private fun vint(keepMarker: Boolean): Long {
        if (position >= data.size) return -1
        val first = data[position].toInt() and 0xFF
        if (first == 0) return -1
        val length = Integer.numberOfLeadingZeros(first) - 23
        if (position + length > data.size) return -1
        var value = if (keepMarker) first.toLong() else (first and (0xFF shr length)).toLong()
        for (i in 1 until length) value = (value shl 8) or (data[position + i].toLong() and 0xFF)
        position += length
        return value
    }
}
