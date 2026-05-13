package eu.darken.sdmse.appcleaner.ui.list

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppCleanerSearchMatcherTest : BaseTest() {

    @Test
    fun `normalize trims and lowercases`() {
        AppCleanerSearchMatcher.normalizeQuery("  WhatsApp  ") shouldBe "whatsapp"
    }

    @Test
    fun `normalize whitespace-only query becomes empty`() {
        AppCleanerSearchMatcher.normalizeQuery("   ") shouldBe ""
    }

    @Test
    fun `empty query matches anything`() {
        AppCleanerSearchMatcher.matches(
            label = "WhatsApp",
            packageName = "com.whatsapp",
            normalizedQuery = "",
        ) shouldBe true
    }

    @Test
    fun `case-insensitive label match`() {
        AppCleanerSearchMatcher.matches(
            label = "WhatsApp",
            packageName = "com.whatsapp",
            normalizedQuery = "whats",
        ) shouldBe true
    }

    @Test
    fun `case-insensitive package-name match`() {
        AppCleanerSearchMatcher.matches(
            label = "Some App",
            packageName = "com.example.thing",
            normalizedQuery = "example",
        ) shouldBe true
    }

    @Test
    fun `substring match anywhere in the string`() {
        AppCleanerSearchMatcher.matches(
            label = "Banking App",
            packageName = "com.foo.bank",
            normalizedQuery = "ank",
        ) shouldBe true
    }

    @Test
    fun `non-matching query returns false`() {
        AppCleanerSearchMatcher.matches(
            label = "WhatsApp",
            packageName = "com.whatsapp",
            normalizedQuery = "telegram",
        ) shouldBe false
    }

    @Test
    fun `label-only match when package does not contain query`() {
        AppCleanerSearchMatcher.matches(
            label = "Telegram",
            packageName = "org.thunderdome",
            normalizedQuery = "telegram",
        ) shouldBe true
    }

    @Test
    fun `package-only match when label does not contain query`() {
        AppCleanerSearchMatcher.matches(
            label = "TG",
            packageName = "org.telegram.messenger",
            normalizedQuery = "telegram",
        ) shouldBe true
    }

    @Test
    fun `diacritics are folded in the query`() {
        AppCleanerSearchMatcher.normalizeQuery("Café") shouldBe "cafe"
    }

    @Test
    fun `diacritics in label match accent-less query`() {
        val normalized = AppCleanerSearchMatcher.normalizeQuery("cafe")
        AppCleanerSearchMatcher.matches(
            label = "Café Bistro",
            packageName = "com.example.cafe",
            normalizedQuery = normalized,
        ) shouldBe true
    }

    @Test
    fun `accent-less label matches diacritic query`() {
        val normalized = AppCleanerSearchMatcher.normalizeQuery("Café")
        AppCleanerSearchMatcher.matches(
            label = "Cafe Bistro",
            packageName = "com.example.cafe",
            normalizedQuery = normalized,
        ) shouldBe true
    }
}
