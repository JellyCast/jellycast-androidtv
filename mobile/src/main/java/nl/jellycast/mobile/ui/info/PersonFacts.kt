package nl.jellycast.mobile.ui.info

import java.time.LocalDate
import java.time.Period
import java.util.Locale
import nl.jellycast.core.model.Birthplace

/** Whole years between [birthDate] and [today]; `null` when unknown or not yet one. */
fun ageInYears(birthDate: LocalDate?, today: LocalDate): Int? {
    if (birthDate == null) return null
    return Period.between(birthDate, today).years.takeIf { it > 0 }
}

/** "Uvalde, United States", the country in [locale]'s language. */
fun birthplaceText(birthplace: Birthplace?, locale: Locale): String? {
    birthplace ?: return null
    val country = birthplace.country ?: return birthplace.place
    return "${birthplace.place}, ${localizedCountry(country, locale)}"
}

/** Country names as TMDb writes them (English), shown in [locale]'s language. */
fun localizedCountry(english: String, locale: Locale): String {
    val code = CountryAliases[english] ?: countryCode(english) ?: return english
    return Locale.Builder().setRegion(code).build().getDisplayCountry(locale).ifEmpty { english }
}

private fun countryCode(englishName: String): String? = Locale.getISOCountries().firstOrNull {
    Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH) == englishName
}

private val CountryAliases =
    mapOf("USA" to "US", "UK" to "GB", "England" to "GB", "Scotland" to "GB", "Wales" to "GB")
