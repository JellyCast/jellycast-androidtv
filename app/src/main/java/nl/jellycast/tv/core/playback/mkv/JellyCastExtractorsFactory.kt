package nl.jellycast.tv.core.playback.mkv

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser

/**
 * Media3's default extractors with the MKV extractor replaced. Subtitle settings mirror
 * [DefaultExtractorsFactory], so subtitles behave exactly the same.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
class JellyCastExtractorsFactory : ExtractorsFactory {
    private val defaults = DefaultExtractorsFactory()
    private var subtitleParserFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory()
    private var textTrackTranscodingEnabled = true

    override fun createExtractors(): Array<Extractor> = replaceMatroska(defaults.createExtractors())

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = replaceMatroska(defaults.createExtractors(uri, responseHeaders))

    override fun experimentalSetTextTrackTranscodingEnabled(
        textTrackTranscodingEnabled: Boolean,
    ): ExtractorsFactory {
        this.textTrackTranscodingEnabled = textTrackTranscodingEnabled
        defaults.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
        return this
    }

    override fun setSubtitleParserFactory(
        subtitleParserFactory: SubtitleParser.Factory,
    ): ExtractorsFactory {
        this.subtitleParserFactory = subtitleParserFactory
        defaults.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }

    private fun replaceMatroska(extractors: Array<Extractor>): Array<Extractor> = extractors
        .map { extractor ->
            if (extractor.javaClass == MatroskaExtractor::class.java) {
                ResyncingMatroskaExtractor(
                    FastCuesMatroskaExtractor(subtitleParserFactory, matroskaFlags()),
                )
            } else {
                extractor
            }
        }.toTypedArray()

    private fun matroskaFlags(): Int =
        if (textTrackTranscodingEnabled) 0 else MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA
}
