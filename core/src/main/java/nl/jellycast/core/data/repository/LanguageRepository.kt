package nl.jellycast.core.data.repository

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
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

/**
 * Per-app languages need Android 13. Before that (phones and tablets on Android 12) the app
 * follows the system language and [setLanguage] has no effect.
 */
class DefaultLanguageRepository(context: Context) : LanguageRepository {
    private val locales = AppLocales.of(context)

    override val languages: List<AppLanguage> =
        locales.supportedTags().let { sortedLanguages(it, DEFAULT_LANGUAGE_TAG) }

    private val _current = MutableStateFlow(languageFor(locales.current()))
    override val current: StateFlow<AppLanguage> = _current.asStateFlow()

    init {
        // The language can also change outside the app (per-app settings, adb); Android reports
        // that as a configuration change.
        context.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    _current.value = languageFor(locales.current())
                }

                @Deprecated("Replaced by onTrimMemory; not needed here.")
                override fun onLowMemory() = Unit
            },
        )
    }

    override fun setLanguage(language: AppLanguage) {
        _current.value = language
        locales.set(LocaleList.forLanguageTags(language.tag))
    }

    override fun applyDefaultIfUnset() {
        if (locales.isUnset()) {
            locales.set(LocaleList.forLanguageTags(DEFAULT_LANGUAGE_TAG))
            _current.value = languageFor(locales.current())
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

private interface AppLocales {
    fun supportedTags(): List<String>

    fun current(): LocaleList

    fun isUnset(): Boolean

    fun set(locales: LocaleList)

    companion object {
        fun of(context: Context): AppLocales = if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            PerAppLocales(context)
        } else {
            SystemLocales
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PerAppLocales(private val context: Context) : AppLocales {
    private val manager = context.getSystemService(LocaleManager::class.java)

    override fun supportedTags(): List<String> = LocaleConfig(context)
        .takeIf { it.status == LocaleConfig.STATUS_SUCCESS }
        ?.supportedLocales
        ?.toLanguageTags()
        ?.split(",")
        .orEmpty()

    override fun current(): LocaleList = manager.applicationLocales

    override fun isUnset(): Boolean = manager.applicationLocales.isEmpty

    override fun set(locales: LocaleList) {
        manager.applicationLocales = locales
    }
}

private object SystemLocales : AppLocales {
    override fun supportedTags(): List<String> = emptyList()

    override fun current(): LocaleList = LocaleList.getDefault()

    override fun isUnset(): Boolean = false

    override fun set(locales: LocaleList) = Unit
}
