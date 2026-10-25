package nl.jellycast.tv.core.model

/**
 * [tag] is a BCP 47 tag ("nl", "pt-BR"); [name] is in the language itself ("Nederlands"), so it
 * is recognisable even when you cannot read the current language.
 */
data class AppLanguage(val tag: String, val name: String)
