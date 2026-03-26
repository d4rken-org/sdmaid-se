package eu.darken.sdmse.common.filter

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class CustomFilterEditorRouteSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `CustomFilterEditorRoute with null initial serialization round-trip`() {
        val original = CustomFilterEditorRoute(
            identifier = "filter-123",
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "identifier": "filter-123"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<CustomFilterEditorRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `CustomFilterEditorRoute with populated options serialization round-trip`() {
        val original = CustomFilterEditorRoute(
            identifier = "filter-456",
            initial = CustomFilterEditorOptions(
                label = "My Custom Filter",
                saveAsEnabled = true,
            ),
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "initial": {
                    "label": "My Custom Filter",
                    "saveAsEnabled": true
                },
                "identifier": "filter-456"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<CustomFilterEditorRoute>(serialized)
        deserialized shouldBe original
    }
}
