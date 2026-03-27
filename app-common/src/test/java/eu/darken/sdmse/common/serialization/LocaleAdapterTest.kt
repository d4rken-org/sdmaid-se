package eu.darken.sdmse.common.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.util.Locale

class LocaleAdapterTest : BaseTest() {

    private val json = SerializationCommonModule().json()

    @Serializable
    data class TestContainer(
        @Serializable(with = LocaleSerializer::class) val value: Locale,
    )

    @Test
    fun `serialize matches golden JSON`() {
        val obj = TestContainer(
            value = Locale.forLanguageTag("en-US"),
        )

        val rawJson = json.encodeToString(TestContainer.serializer(), obj)

        rawJson.toComparableJson() shouldBe """
            {
                "value": "en-US"
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialize from golden JSON`() {
        val jsonStr = """{ "value": "en-US" }"""
        val obj = json.decodeFromString(TestContainer.serializer(), jsonStr)
        obj.value shouldBe Locale.forLanguageTag("en-US")
    }

    @Test
    fun `serialize language only`() {
        val obj = TestContainer(value = Locale.forLanguageTag("de"))
        val rawJson = json.encodeToString(TestContainer.serializer(), obj)
        rawJson.toComparableJson() shouldBe """
            {
                "value": "de"
            }
        """.toComparableJson()
    }

    @Test
    fun `round trip preserves value`() {
        val original = TestContainer(
            value = Locale.forLanguageTag("zh-Hans-CN"),
        )
        val jsonStr = json.encodeToString(TestContainer.serializer(), original)
        val restored = json.decodeFromString(TestContainer.serializer(), jsonStr)
        restored shouldBe original
    }
}
