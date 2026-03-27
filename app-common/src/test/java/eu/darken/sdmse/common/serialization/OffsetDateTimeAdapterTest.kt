package eu.darken.sdmse.common.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OffsetDateTimeAdapterTest : BaseTest() {

    private val json = SerializationCommonModule().json()

    @Serializable
    data class TestContainer(
        @Serializable(with = OffsetDateTimeSerializer::class) val value: OffsetDateTime,
    )

    @Test
    fun `serialize matches golden JSON`() {
        val obj = TestContainer(
            value = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(1)),
        )

        val rawJson = json.encodeToString(TestContainer.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "value": "2024-01-15T10:30:00+01:00"
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """{ "value": "2024-01-15T10:30:00+01:00" }"""
        val obj = json.decodeFromString(TestContainer.serializer(), jsonStr)
        obj.value shouldBe OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(1))
    }

    @Test
    fun `serialize UTC offset`() {
        val obj = TestContainer(
            value = OffsetDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC),
        )
        val rawJson = json.encodeToString(TestContainer.serializer(), obj)
        rawJson.toComparableJson() shouldBe """
            {
                "value": "2024-06-01T00:00:00Z"
            }
        """.toComparableJson()
    }

    @Test
    fun `round trip preserves value`() {
        val original = TestContainer(
            value = OffsetDateTime.of(2023, 12, 31, 23, 59, 59, 0, ZoneOffset.ofHours(-5)),
        )
        val jsonStr = json.encodeToString(TestContainer.serializer(), original)
        val restored = json.decodeFromString(TestContainer.serializer(), jsonStr)
        restored shouldBe original
    }
}
