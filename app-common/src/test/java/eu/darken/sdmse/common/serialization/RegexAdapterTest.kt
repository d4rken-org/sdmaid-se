package eu.darken.sdmse.common.serialization

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class RegexAdapterTest : BaseTest() {

    val moshi = Moshi.Builder().apply {
        add(RegexAdapter())
    }.build()

    @JsonClass(generateAdapter = true)
    data class TestContainer(
        val regexValue: Regex?,
        val regexList: List<Regex>
    )

    val adapter = moshi.adapter(TestContainer::class.java)

    @Test
    fun `serialize test container`() {
        val before = TestContainer(
            regexValue = Regex("value", RegexOption.LITERAL),
            regexList = listOf(
                Regex("ele1", RegexOption.COMMENTS),
                Regex("ele2", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)),
            )
        )

        val rawJson = adapter.toJson(before)

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

        val after = adapter.fromJson(rawJson)!!
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