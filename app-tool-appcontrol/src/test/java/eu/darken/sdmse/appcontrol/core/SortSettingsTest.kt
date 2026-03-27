package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class SortSettingsTest : BaseTest() {

    private val json: Json = SerializationIOModule().json()

    @Test
    fun `serialize default matches golden JSON`() {
        val obj = SortSettings()

        val rawJson = json.encodeToString(SortSettings.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "mode": "LAST_UPDATE",
                "reversed": true
            }
        """.toComparableJson()
    }

    @Test
    fun `serialize custom values matches golden JSON`() {
        val obj = SortSettings(
            mode = SortSettings.Mode.NAME,
            reversed = false,
        )

        val rawJson = json.encodeToString(SortSettings.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "mode": "NAME",
                "reversed": false
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """{ "mode": "SIZE", "reversed": true }"""
        val obj = json.decodeFromString(SortSettings.serializer(), jsonStr)
        obj shouldBe SortSettings(
            mode = SortSettings.Mode.SIZE,
            reversed = true,
        )
    }

    @Test
    fun `deserialize all mode values`() {
        SortSettings.Mode.entries.forEach { mode ->
            val jsonStr = """{ "mode": "${mode.name}", "reversed": false }"""
            val obj = json.decodeFromString(SortSettings.serializer(), jsonStr)
            obj.mode shouldBe mode
        }
    }

    @Test
    fun `round trip preserves value`() {
        val original = SortSettings(
            mode = SortSettings.Mode.SCREEN_TIME,
            reversed = false,
        )
        val jsonStr = json.encodeToString(SortSettings.serializer(), original)
        val restored = json.decodeFromString(SortSettings.serializer(), jsonStr)
        restored shouldBe original
    }
}
