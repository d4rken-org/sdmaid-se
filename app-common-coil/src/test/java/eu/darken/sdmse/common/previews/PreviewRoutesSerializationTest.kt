package eu.darken.sdmse.common.previews

import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class PreviewRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `PreviewRoute serialization round-trip`() {
        val original = PreviewRoute(
            options = PreviewOptions(
                paths = listOf(
                    LocalPath.build("/test/image.jpg"),
                    LocalPath.build("/test/photo.png"),
                ),
                position = 1,
            ),
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "options": {
                    "paths": [
                        {
                            "file": "/test/image.jpg"
                        },
                        {
                            "file": "/test/photo.png"
                        }
                    ],
                    "position": 1
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<PreviewRoute>(serialized)
        deserialized shouldBe original
    }
}
