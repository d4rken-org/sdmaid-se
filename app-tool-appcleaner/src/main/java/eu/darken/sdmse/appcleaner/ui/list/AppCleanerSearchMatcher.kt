package eu.darken.sdmse.appcleaner.ui.list

import java.text.Normalizer

internal object AppCleanerSearchMatcher {

    private val combiningMarks = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** Trim, lowercase, and strip diacritics so `Café` matches a `cafe` query. */
    fun normalize(raw: String): String = Normalizer
        .normalize(raw.trim(), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase()

    fun normalizeQuery(raw: String): String = normalize(raw)

    fun matches(label: String, packageName: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        return normalize(label).contains(normalizedQuery)
            || normalize(packageName).contains(normalizedQuery)
    }
}
