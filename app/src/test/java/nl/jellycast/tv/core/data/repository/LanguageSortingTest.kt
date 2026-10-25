package nl.jellycast.tv.core.data.repository

import nl.jellycast.tv.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSortingTest {
    @Test
    fun `default language first, then A-Z by native name`() {
        val languages = sortedLanguages(listOf("fr", "en", "nl", "es"), defaultTag = "nl")

        assertEquals(
            listOf("Nederlands", "English", "Español", "Français"),
            languages.map { it.name },
        )
    }

    @Test
    fun `the default language is present even when the list is empty`() {
        assertEquals(listOf(AppLanguage("nl", "Nederlands")), sortedLanguages(emptyList(), "nl"))
    }

    @Test
    fun `a regional variant gets the region in its own language`() {
        assertEquals(AppLanguage("pt-BR", "Português (Brasil)"), appLanguage("pt-BR"))
    }
}
