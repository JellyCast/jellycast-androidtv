package nl.jellycast.core.data.repository

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.jellycast.core.model.AppLanguage

// Must match `unqualifiedResLocale` in res/resources.properties.
const val DEFAULT_LANGUAGE_TAG = "en"

/**
 * Available languages come from the LocaleConfig Gradle generates from the values-* folders.
 * Android persists the chosen language itself.
 */
interface LanguageRepository {
    val languages: List<AppLanguage>

    val current: StateFlow<AppLanguage>

    fun setLanguage(language: AppLanguage)

    /** Without a choice Android would follow the TV's language. */
    fun applyDefaultIfUnset()
}

class DefaultLanguageRepository(context: Context) : LanguageRepository {
    private val localeManager = context.getSystemService(LocaleManager::class.java)

    override val languages: List<AppLanguage> =
        LocaleConfig(context)
            .takeIf { it.status == LocaleConfig.STATUS_SUCCESS }
            ?.supportedLocales
            ?.toLanguageTags()
            ?.split(",")
            .orEmpty()
            .let { sortedLanguages(it, DEFAULT_LANGUAGE_TAG) }

    private val _current = MutableStateFlow(languageFor(localeManager.applicationLocales))
    override val current: StateFlow<AppLanguage> = _current.asStateFlow()

    init {
        // The language can also change outside the app (per-app settings, adb); Android reports
        // that as a configuration change.
        context.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    _current.value = languageFor(localeManager.applicationLocales)
                }

                @Deprecated("Replaced by onTrimMemory; not needed here.")
                override fun onLowMemory() = Unit
            },
        )
    }

    override fun setLanguage(language: AppLanguage) {
        _current.value = language
        localeManager.applicationLocales = LocaleList.forLanguageTags(language.tag)
    }

    override fun applyDefaultIfUnset() {
        if (localeManager.applicationLocales.isEmpty) {
            localeManager.applicationLocales = LocaleList.forLanguageTags(DEFAULT_LANGUAGE_TAG)
            _current.value = languageFor(localeManager.applicationLocales)
        }
    }

    private fun languageFor(locales: LocaleList): AppLanguage {
        val tag = locales.get(0)?.toLanguageTag() ?: DEFAULT_LANGUAGE_TAG
        return languages.firstOrNull { it.tag == tag }
            ?: languages.firstOrNull { it.tag == DEFAULT_LANGUAGE_TAG }
            ?: appLanguage(tag)
    }
}

/** The default language first (even if missing from [tags]), then A–Z by native name. */
internal fun sortedLanguages(tags: List<String>, defaultTag: String): List<AppLanguage> {
    val others =
        tags
            .filter { it.isNotBlank() && it != defaultTag }
            .distinct()
            .map(::appLanguage)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    return listOf(appLanguage(defaultTag)) + others
}

/** "es" → "Español", "pt-BR" → "Português (Brasil)". */
internal fun appLanguage(tag: String): AppLanguage {
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    return AppLanguage(tag = tag, name = name)
}
