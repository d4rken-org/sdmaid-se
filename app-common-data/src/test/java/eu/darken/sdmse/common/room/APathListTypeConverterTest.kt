package eu.darken.sdmse.common.room

import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class APathListTypeConverterTest : BaseTest() {

    private val json = SerializationIOModule().json()
    private val converter = APathListTypeConverter(json)

    @Test
    fun `serialize mixed path list matches golden JSON`() {
        val paths = listOf(
            LocalPath.build(file = File("/data/path")),
            RawPath.build("/raw/path"),
        )

        val jsonStr = converter.from(paths)

        jsonStr.toComparableJson() shouldBe """
            [
                {
                    "file": "/data/path",
                    "pathType": "LOCAL"
                },
                {
                    "path": "/raw/path",
                    "pathType": "RAW"
                }
            ]
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """[{"file":"/data/path","pathType":"LOCAL"},{"path":"/raw","pathType":"RAW"}]"""
        val paths = converter.to(jsonStr)
        paths shouldBe listOf(
            LocalPath.build(file = File("/data/path")),
            RawPath.build("/raw"),
        )
    }

    @Test
    fun `serialize empty list`() {
        val jsonStr = converter.from(emptyList())
        jsonStr.toComparableJson() shouldBe "[]".toComparableJson()
    }

    @Test
    fun `deserialize empty list`() {
        val paths = converter.to("[]")
        paths shouldBe emptyList()
    }

    @Test
    fun `round trip preserves list`() {
        val original = listOf(
            LocalPath.build(file = File("/a")),
            LocalPath.build(file = File("/b")),
            RawPath.build("/c"),
        )
        val jsonStr = converter.from(original)
        val restored = converter.to(jsonStr)
        restored shouldBe original
    }
}
