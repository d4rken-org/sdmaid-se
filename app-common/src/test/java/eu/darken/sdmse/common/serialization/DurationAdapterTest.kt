package eu.darken.sdmse.common.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Duration

class DurationAdapterTest : BaseTest() {

    private val json = SerializationCommonModule().json()

    @Serializable
    data class TestContainer(
        @Serializable(with = DurationSerializer::class) val value: Duration,
    )

    @Test
    fun `serialize matches golden JSON`() {
        val obj = TestContainer(
            value = Duration.ofHours(72),
        )

        val rawJson = json.encodeToString(TestContainer.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "value": "PT72H"
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """{ "value": "PT72H" }"""
        val obj = json.decodeFromString(TestContainer.serializer(), jsonStr)
        obj.value shouldBe Duration.ofHours(72)
    }

    @Test
    fun `serialize zero duration`() {
        val obj = TestContainer(value = Duration.ZERO)
        val rawJson = json.encodeToString(TestContainer.serializer(), obj)
        rawJson.toComparableJson() shouldBe """
            {
                "value": "PT0S"
            }
        """.toComparableJson()
    }

    @Test
    fun `round trip preserves value`() {
        val original = TestContainer(
            value = Duration.ofMinutes(30),
        )
        val jsonStr = json.encodeToString(TestContainer.serializer(), original)
        val restored = json.decodeFromString(TestContainer.serializer(), jsonStr)
        restored shouldBe original
    }
}
