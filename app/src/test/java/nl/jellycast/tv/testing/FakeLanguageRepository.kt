package nl.jellycast.tv.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.jellycast.tv.core.data.repository.LanguageRepository
import nl.jellycast.tv.core.model.AppLanguage

val Dutch = AppLanguage("nl", "Nederlands")
val English = AppLanguage("en", "English")
val Spanish = AppLanguage("es", "Español")

class FakeLanguageRepository(
    override val languages: List<AppLanguage> = listOf(Dutch, English, Spanish),
    initial: AppLanguage = languages.first(),
) : LanguageRepository {
    private val _current = MutableStateFlow(initial)
    override val current: StateFlow<AppLanguage> = _current

    override fun setLanguage(language: AppLanguage) {
        _current.value = language
    }

    override fun applyDefaultIfUnset() = Unit
}
