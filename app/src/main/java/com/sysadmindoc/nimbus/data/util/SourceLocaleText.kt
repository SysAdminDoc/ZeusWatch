package com.sysadmindoc.nimbus.data.util

import java.util.Locale

internal data class LocalizedTextOption(
    val languageTag: String?,
    val text: String?,
)

internal object SourceLocaleText {
    fun preferredLanguage(
        supportedLanguages: Set<String>,
        fallback: String = "en",
        locale: Locale = Locale.getDefault(),
    ): String {
        val supportedByLanguage = supportedLanguages.associateBy { it.lowercase(Locale.ROOT) }
        return supportedByLanguage[locale.language.lowercase(Locale.ROOT)]
            ?: supportedByLanguage[fallback.lowercase(Locale.ROOT)]
            ?: supportedLanguages.first()
    }

    fun preferredHkoLanguage(locale: Locale = Locale.getDefault()): String {
        if (!locale.language.equals("zh", ignoreCase = true)) return "en"
        val script = inferredChineseScript(locale)
        return if (script == "Hans") "sc" else "tc"
    }

    fun preferredEnvironmentCanadaAlertSuffix(locale: Locale = Locale.getDefault()): String =
        if (locale.language.equals("fr", ignoreCase = true)) "f" else "e"

    fun selectText(
        options: List<LocalizedTextOption>,
        fallback: String? = null,
        locale: Locale = Locale.getDefault(),
    ): String? {
        val localized = options.firstOrNull { languageMatches(it.languageTag, locale) }
            ?.text
            ?.takeUnlessBlank()
        return localized ?: fallback.takeUnlessBlank()
    }

    fun <T> filterByLocale(
        items: List<T>,
        languageTag: (T) -> String?,
        locale: Locale = Locale.getDefault(),
    ): List<T> {
        val matched = items.filter { languageMatches(languageTag(it), locale) }
        return matched.ifEmpty { items }
    }

    fun languageMatches(languageTag: String?, locale: Locale = Locale.getDefault()): Boolean {
        if (languageTag.isNullOrBlank()) return false
        val source = Locale.forLanguageTag(languageTag.replace('_', '-'))
        if (source.language.isBlank()) return false
        if (!source.language.equals(locale.language, ignoreCase = true)) return false

        if (source.language.equals("zh", ignoreCase = true) && source.script.isNotBlank()) {
            return source.script.equals(inferredChineseScript(locale), ignoreCase = true)
        }
        return true
    }

    private fun String?.takeUnlessBlank(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }

    private fun inferredChineseScript(locale: Locale): String {
        if (locale.script.isNotBlank()) return locale.script
        return when (locale.country.uppercase(Locale.ROOT)) {
            "CN", "SG" -> "Hans"
            "HK", "MO", "TW" -> "Hant"
            else -> "Hant"
        }
    }
}
