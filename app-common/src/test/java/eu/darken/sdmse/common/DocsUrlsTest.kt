package eu.darken.sdmse.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.Locale

class DocsUrlsTest : BaseTest() {

    private fun localize(address: String, vararg tags: String) =
        DocsUrls.localize(address, tags.map { Locale.forLanguageTag(it) })

    @Test
    fun `german locale gets the german page`() {
        localize("https://sdmse.darken.eu/setup#shizuku", "de") shouldBe "https://sdmse.darken.eu/de/setup#shizuku"
        localize("https://sdmse.darken.eu/appcleaner#settings", "de-AT") shouldBe
            "https://sdmse.darken.eu/de/appcleaner#settings"
    }

    @Test
    fun `any portuguese gets brazilian portuguese`() {
        localize("https://sdmse.darken.eu/setup", "pt-PT") shouldBe "https://sdmse.darken.eu/pt-br/setup"
        localize("https://sdmse.darken.eu/setup", "pt-BR") shouldBe "https://sdmse.darken.eu/pt-br/setup"
    }

    @Test
    fun `old and new indonesian codes both match`() {
        localize("https://sdmse.darken.eu/setup", "in") shouldBe "https://sdmse.darken.eu/id/setup"
        localize("https://sdmse.darken.eu/setup", "id") shouldBe "https://sdmse.darken.eu/id/setup"
    }

    @Test
    fun `simplified chinese locales get simplified chinese`() {
        listOf("zh-CN", "zh-SG", "zh-Hans", "zh", "zh-Hans-HK").forEach {
            localize("https://sdmse.darken.eu/setup", it) shouldBe "https://sdmse.darken.eu/zh-hans/setup"
        }
    }

    @Test
    fun `traditional chinese locales are not rewritten`() {
        listOf("zh-TW", "zh-HK", "zh-MO", "zh-Hant", "zh-Hant-CN").forEach {
            localize("https://sdmse.darken.eu/setup", it) shouldBe "https://sdmse.darken.eu/setup"
        }
    }

    @Test
    fun `unsupported language is not rewritten`() {
        localize("https://sdmse.darken.eu/setup", "ja") shouldBe "https://sdmse.darken.eu/setup"
        localize("https://sdmse.darken.eu/setup", "en-US") shouldBe "https://sdmse.darken.eu/setup"
    }

    @Test
    fun `first supported locale in the list wins`() {
        localize("https://sdmse.darken.eu/setup", "ja", "de") shouldBe "https://sdmse.darken.eu/de/setup"
        localize("https://sdmse.darken.eu/setup", "fr", "de") shouldBe "https://sdmse.darken.eu/fr/setup"
        localize("https://sdmse.darken.eu/setup", "zh-TW", "de") shouldBe "https://sdmse.darken.eu/de/setup"
    }

    @Test
    fun `empty locale list is not rewritten`() {
        localize("https://sdmse.darken.eu/setup") shouldBe "https://sdmse.darken.eu/setup"
    }

    @Test
    fun `english only pages are not rewritten`() {
        localize("https://sdmse.darken.eu/privacy", "de") shouldBe "https://sdmse.darken.eu/privacy"
        localize("https://sdmse.darken.eu/changelog", "de") shouldBe "https://sdmse.darken.eu/changelog"
        localize("https://sdmse.darken.eu/security", "de") shouldBe "https://sdmse.darken.eu/security"
    }

    @Test
    fun `already localized pages are not rewritten`() {
        localize("https://sdmse.darken.eu/de/setup#shizuku", "fr") shouldBe "https://sdmse.darken.eu/de/setup#shizuku"
        localize("https://sdmse.darken.eu/zh-hans/setup", "de") shouldBe "https://sdmse.darken.eu/zh-hans/setup"
        localize("https://sdmse.darken.eu/pt-br/", "de") shouldBe "https://sdmse.darken.eu/pt-br/"
    }

    @Test
    fun `root gets the language root`() {
        localize("https://sdmse.darken.eu/", "de") shouldBe "https://sdmse.darken.eu/de/"
        localize("https://sdmse.darken.eu", "de") shouldBe "https://sdmse.darken.eu/de/"
    }

    @Test
    fun `query and fragment are kept`() {
        localize("https://sdmse.darken.eu/setup?x=1#shizuku", "de") shouldBe "https://sdmse.darken.eu/de/setup?x=1#shizuku"
        localize("https://sdmse.darken.eu/setup?x=1", "de") shouldBe "https://sdmse.darken.eu/de/setup?x=1"
        localize("https://sdmse.darken.eu?x=1", "de") shouldBe "https://sdmse.darken.eu/de/?x=1"
    }

    @Test
    fun `other hosts and schemes are not rewritten`() {
        localize("https://example.com/setup#shizuku", "de") shouldBe "https://example.com/setup#shizuku"
        localize("https://github.com/sponsors/d4rken", "de") shouldBe "https://github.com/sponsors/d4rken"
        localize("https://sdmse.darken.eu.example.com/setup", "de") shouldBe "https://sdmse.darken.eu.example.com/setup"
        localize("http://sdmse.darken.eu/setup", "de") shouldBe "http://sdmse.darken.eu/setup"
    }
}
