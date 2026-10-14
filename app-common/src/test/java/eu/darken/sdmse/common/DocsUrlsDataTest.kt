package eu.darken.sdmse.common

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class DocsUrlsDataTest : BaseTest() {

    private fun readJson(path: String) = Json.parseToJsonElement(File(path).readText())

    @Test
    fun `language paths match the docs site languages`() {
        val languages = readJson("../docs/_data/languages.json").jsonArray.associate {
            it.jsonObject.getValue("code").jsonPrimitive.content to it.jsonObject.getValue("path").jsonPrimitive.content
        }

        DocsUrls.LANGUAGE_PATHS shouldBe languages
    }

    @Test
    fun `english only pages match the docs tooling list`() {
        val englishOnly = readJson("../tooling/docs/english_only.json").jsonArray.map { it.jsonPrimitive.content }

        DocsUrls.ENGLISH_ONLY shouldBe englishOnly.toSet()
    }
}
