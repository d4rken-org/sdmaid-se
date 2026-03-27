package eu.darken.sdmse.common.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class RegexAdapterTest : BaseTest() {

    val json = SerializationCommonModule().json()

    @Serializable
    data class TestContainer(
        @Serializable(with = RegexSerializer::class) val regexValue: Regex?,
        val regexList: List<@Serializable(with = RegexSerializer::class) Regex>
    )

    @Test
    fun `serialize test container`() {
        val before = TestContainer(
            regexValue = Regex("value", RegexOption.LITERAL),
            regexList = listOf(
                Regex("ele1", RegexOption.COMMENTS),
                Regex("ele2", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)),
            )
        )

        val rawJson = json.encodeToString(TestContainer.serializer(), before)

        rawJson.toComparableJson() shouldBe """
            {
                "regexValue": {
                    "pattern": "value",
                    "options": [
                        "LITERAL"
                    ]
                },
                "regexList": [
                    {
                        "pattern": "ele1",
                        "options": [
                            "COMMENTS"
                        ]
                    },
                    {
                        "pattern": "ele2",
                        "options": [
                            "MULTILINE",
                            "DOT_MATCHES_ALL"
                        ]
                    }
                ]
            }
        """.toComparableJson()

        val after = json.decodeFromString(TestContainer.serializer(), rawJson)
        after.regexValue!!.apply {
            pattern shouldBe before.regexValue!!.pattern
            options shouldBe before.regexValue.options
        }
        after.regexList[0].apply {
            pattern shouldBe before.regexList[0].pattern
            options shouldBe before.regexList[0].options
        }
        after.regexList[1].apply {
            pattern shouldBe before.regexList[1].pattern
            options shouldBe before.regexList[1].options
        }

    }
}
