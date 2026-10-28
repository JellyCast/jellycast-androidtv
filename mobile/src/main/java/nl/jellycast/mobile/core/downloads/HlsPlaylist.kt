package nl.jellycast.mobile.core.downloads

import java.net.URI
import java.util.Locale
import kotlin.math.ceil
import kotlinx.serialization.Serializable

/** The variant playlist of a Jellyfin HLS transcode: the fMP4 init segment and the media segments. */
data class HlsPlaylist(val initSegmentUrl: String, val segments: List<Segment>) {
    data class Segment(val url: String, val seconds: Double)

    val totalSeconds: Double get() = segments.sumOf { it.seconds }

    companion object {
        /** The first variant a master playlist points to, resolved against [masterUrl]. */
        fun variantUrl(masterText: String, masterUrl: String): String? =
            masterText.lines().map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?.let { resolve(masterUrl, it) }

        fun parse(text: String, baseUrl: String): HlsPlaylist? {
            var initSegment: String? = null
            val segments = mutableListOf<Segment>()
            var pendingSeconds: Double? = null
            for (line in text.lines().map { it.trim() }) {
                when {
                    line.startsWith(MAP_TAG) ->
                        initSegment =
                            line.substringAfter("URI=\"", "").substringBefore('"')
                                .takeIf { it.isNotEmpty() }
                                ?.let { resolve(baseUrl, it) }

                    line.startsWith(INF_TAG) ->
                        pendingSeconds = line.removePrefix(INF_TAG).substringBefore(',')
                            .toDoubleOrNull()

                    line.isNotEmpty() && !line.startsWith("#") -> {
                        val seconds = pendingSeconds ?: continue
                        segments += Segment(resolve(baseUrl, line), seconds)
                        pendingSeconds = null
                    }
                }
            }
            if (initSegment == null || segments.isEmpty()) return null
            return HlsPlaylist(initSegment, segments)
        }

        private fun resolve(base: String, reference: String): String =
            URI(base).resolve(reference).toString()

        private const val MAP_TAG = "#EXT-X-MAP:"
        private const val INF_TAG = "#EXTINF:"
    }
}

/** One saved segment of a Compact download: its length in the file and its duration. */
@Serializable
data class SavedSegment(val bytes: Long, val seconds: Double)

/**
 * A local VOD playlist over the one appended file, with a byte range per segment. Media3 can't
 * seek in a fragmented MP4 without an index, but it seeks in HLS.
 */
fun localPlaylist(fileName: String, initBytes: Long, segments: List<SavedSegment>): String {
    val target = segments.maxOfOrNull { it.seconds }?.let { ceil(it).toInt() } ?: 1
    return buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:7")
        appendLine("#EXT-X-TARGETDURATION:$target")
        appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
        appendLine("#EXT-X-MAP:URI=\"$fileName\",BYTERANGE=\"$initBytes@0\"")
        var offset = initBytes
        for (segment in segments) {
            appendLine("#EXTINF:${String.format(Locale.ROOT, "%.6f", segment.seconds)},")
            appendLine("#EXT-X-BYTERANGE:${segment.bytes}@$offset")
            appendLine(fileName)
            offset += segment.bytes
        }
        appendLine("#EXT-X-ENDLIST")
    }
}
