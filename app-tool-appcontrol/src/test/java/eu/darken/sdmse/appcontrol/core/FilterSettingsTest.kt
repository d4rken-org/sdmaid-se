package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class FilterSettingsTest : BaseTest() {

    private val json: Json = SerializationIOModule().json()

    @Test
    fun `serialize default matches golden JSON`() {
        val obj = FilterSettings()

        val rawJson = json.encodeToString(FilterSettings.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "tags": [
                    "USER",
                    "ENABLED"
                ]
            }
        """.toComparableJson()
    }

    @Test
    fun `serialize all tags matches golden JSON`() {
        val obj = FilterSettings(
            tags = setOf(
                FilterSettings.Tag.USER,
                FilterSettings.Tag.SYSTEM,
                FilterSettings.Tag.ENABLED,
                FilterSettings.Tag.DISABLED,
            ),
        )

        val rawJson = json.encodeToString(FilterSettings.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "tags": [
                    "USER",
                    "SYSTEM",
                    "ENABLED",
                    "DISABLED"
                ]
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """{ "tags": ["SYSTEM", "DISABLED"] }"""
        val obj = json.decodeFromString(FilterSettings.serializer(), jsonStr)
        obj shouldBe FilterSettings(
            tags = setOf(
                FilterSettings.Tag.SYSTEM,
                FilterSettings.Tag.DISABLED,
            ),
        )
    }

    @Test
    fun `deserialize empty tags`() {
        val jsonStr = """{ "tags": [] }"""
        val obj = json.decodeFromString(FilterSettings.serializer(), jsonStr)
        obj shouldBe FilterSettings(tags = emptySet())
    }

    @Test
    fun `round trip preserves value`() {
        val original = FilterSettings(
            tags = setOf(FilterSettings.Tag.ACTIVE, FilterSettings.Tag.NOT_INSTALLED),
        )
        val jsonStr = json.encodeToString(FilterSettings.serializer(), original)
        val restored = json.decodeFromString(FilterSettings.serializer(), jsonStr)
        restored shouldBe original
    }
}
