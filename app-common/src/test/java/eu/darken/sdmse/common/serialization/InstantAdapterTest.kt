package eu.darken.sdmse.common.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class InstantAdapterTest : BaseTest() {

    private val json = SerializationCommonModule().json()

    @Serializable
    data class TestContainer(
        @Serializable(with = InstantSerializer::class) val value: Instant,
    )

    @Test
    fun `serialize matches golden JSON`() {
        val obj = TestContainer(
            value = Instant.parse("2024-01-15T10:30:00Z"),
        )

        val rawJson = json.encodeToString(TestContainer.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "value": "2024-01-15T10:30:00Z"
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """{ "value": "2024-01-15T10:30:00Z" }"""
        val obj = json.decodeFromString(TestContainer.serializer(), jsonStr)
        obj.value shouldBe Instant.parse("2024-01-15T10:30:00Z")
    }

    @Test
    fun `round trip preserves value`() {
        val original = TestContainer(
            value = Instant.parse("2023-06-15T23:59:59.123Z"),
        )
        val jsonStr = json.encodeToString(TestContainer.serializer(), original)
        val restored = json.decodeFromString(TestContainer.serializer(), jsonStr)
        restored shouldBe original
    }
}
