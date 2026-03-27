package eu.darken.sdmse.common.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class FileAdapterTest : BaseTest() {

    private val json = SerializationCommonModule().json()

    @Serializable
    data class TestContainer(
        @Serializable(with = FileSerializer::class) val value: File,
    )

    @Test
    fun `serialize matches golden JSON`() {
        val obj = TestContainer(
            value = File("/data/user/0/eu.darken.sdmse/files"),
        )

        val rawJson = json.encodeToString(TestContainer.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "value": "/data/user/0/eu.darken.sdmse/files"
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """{ "value": "/data/user/0/eu.darken.sdmse/files" }"""
        val obj = json.decodeFromString(TestContainer.serializer(), jsonStr)
        obj.value shouldBe File("/data/user/0/eu.darken.sdmse/files")
    }

    @Test
    fun `round trip preserves relative path`() {
        val original = TestContainer(
            value = File("./relative/path"),
        )
        val jsonStr = json.encodeToString(TestContainer.serializer(), original)
        val restored = json.decodeFromString(TestContainer.serializer(), jsonStr)
        restored shouldBe original
    }
}
