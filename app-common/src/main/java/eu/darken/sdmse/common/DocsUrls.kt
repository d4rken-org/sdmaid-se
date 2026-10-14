package eu.darken.sdmse.common

import java.util.Locale

internal object DocsUrls {

    private const val ORIGIN = "https://sdmse.darken.eu"

    // Docs language code to URL path, pinned to docs/_data/languages.json by DocsUrlsDataTest
    val LANGUAGE_PATHS = mapOf(
        "de" to "de",
        "es" to "es",
        "pt-BR" to "pt-br",
        "ru" to "ru",
        "fr" to "fr",
        "id" to "id",
        "it" to "it",
        "ar" to "ar",
        "tr" to "tr",
        "zh-Hans" to "zh-hans",
        "uk" to "uk",
        "vi" to "vi",
        "pl" to "pl",
    )

    // Pinned to tooling/docs/english_only.json by DocsUrlsDataTest
    val ENGLISH_ONLY = setOf("changelog", "privacy", "security")

    private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")

    /**
     * e.g. https://sdmse.darken.eu/setup#shizuku with [de-AT] -> https://sdmse.darken.eu/de/setup#shizuku
     */
    fun localize(address: String, locales: List<Locale>): String {
        if (!address.startsWith(ORIGIN)) return address
        val rest = address.removePrefix(ORIGIN)
        if (rest.isNotEmpty() && rest[0] !in "/?#") return address

        val pathEnd = rest.indexOfFirst { it == '?' || it == '#' }.takeIf { it >= 0 } ?: rest.length
        val path = rest.substring(0, pathEnd).ifEmpty { "/" }
        val suffix = rest.substring(pathEnd)

        val firstSegment = path.removePrefix("/").substringBefore('/')
        if (firstSegment in ENGLISH_ONLY || firstSegment in LANGUAGE_PATHS.values) return address

        val languagePath = locales.firstNotNullOfOrNull { docsCode(it) }?.let { LANGUAGE_PATHS.getValue(it) }
            ?: return address

        return "$ORIGIN/$languagePath$path$suffix"
    }

    // e.g. de-AT -> de, pt-PT -> pt-BR, zh-CN -> zh-Hans, zh-TW -> none
    private fun docsCode(locale: Locale): String? {
        val code = when (locale.language) {
            "pt" -> "pt-BR"
            "in", "id" -> "id"
            "zh" -> when {
                locale.script == "Hant" -> null
                locale.script != "Hans" && locale.country in TRADITIONAL_CHINESE_REGIONS -> null
                else -> "zh-Hans"
            }

            else -> locale.language
        }
        return code?.takeIf { it in LANGUAGE_PATHS }
    }
}
