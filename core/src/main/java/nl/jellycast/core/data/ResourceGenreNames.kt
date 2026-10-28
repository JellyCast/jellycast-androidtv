package nl.jellycast.core.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.text.Collator
import java.util.Locale
import nl.jellycast.core.common.genreStringRes
import nl.jellycast.core.data.repository.LanguageRepository
import nl.jellycast.core.model.GenreNames

/**
 * Reads the language on every call: ViewModels survive a language switch, and their next load
 * must translate and sort in the new language.
 */
class ResourceGenreNames(private val context: Context, private val languages: LanguageRepository) :
    GenreNames {
    private var cached: Pair<String, Resources>? = null

    override fun translate(serverName: String): String =
        genreStringRes(serverName)?.let { resources().getString(it) } ?: serverName

    override fun order(): Comparator<String> {
        val collator = Collator.getInstance(Locale.forLanguageTag(languages.current.value.tag))
        return Comparator { a, b -> collator.compare(a, b) }
    }

    private fun resources(): Resources {
        val tag = languages.current.value.tag
        cached?.let { (cachedTag, resources) -> if (cachedTag == tag) return resources }
        val configuration =
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
        return context.createConfigurationContext(configuration).resources.also {
            cached = tag to it
        }
    }
}
