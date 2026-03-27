package eu.darken.sdmse.common.picker

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class PickerRouteSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `PickerRoute serialization round-trip`() {
        val original = PickerRoute(
            request = PickerRequest(
                requestKey = "test-key",
                mode = PickerRequest.PickMode.DIR,
                allowedAreas = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
                selectedPaths = listOf(
                    LocalPath.build("/storage/emulated/0/DCIM"),
                ),
            ),
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "request": {
                    "requestKey": "test-key",
                    "mode": "DIR",
                    "allowedAreas": ["SDCARD", "PUBLIC_DATA"],
                    "selectedPaths": [
                        {
                            "file": "/storage/emulated/0/DCIM"
                        }
                    ]
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<PickerRoute>(serialized)
        deserialized shouldBe original
    }
}
