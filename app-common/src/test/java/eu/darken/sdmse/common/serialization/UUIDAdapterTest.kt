package eu.darken.sdmse.common.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.util.UUID

class UUIDAdapterTest : BaseTest() {

    private val json = SerializationCommonModule().json()

    @Serializable
    data class TestContainer(
        @Serializable(with = UUIDSerializer::class) val value: UUID,
    )

    @Test
    fun `serialize matches golden JSON`() {
        val obj = TestContainer(
            value = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
        )

        val rawJson = json.encodeToString(TestContainer.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "value": "550e8400-e29b-41d4-a716-446655440000"
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """{ "value": "550e8400-e29b-41d4-a716-446655440000" }"""
        val obj = json.decodeFromString(TestContainer.serializer(), jsonStr)
        obj.value shouldBe UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    }

    @Test
    fun `round trip preserves value`() {
        val original = TestContainer(
            value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
        )
        val jsonStr = json.encodeToString(TestContainer.serializer(), original)
        val restored = json.decodeFromString(TestContainer.serializer(), jsonStr)
        restored shouldBe original
    }
}
