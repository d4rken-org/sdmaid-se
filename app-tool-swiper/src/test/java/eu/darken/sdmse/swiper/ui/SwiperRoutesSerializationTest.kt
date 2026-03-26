package eu.darken.sdmse.swiper.ui

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class SwiperRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `SwiperSwipeRoute with default startIndex`() {
        val original = SwiperSwipeRoute(sessionId = "session-abc")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "sessionId": "session-abc"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SwiperSwipeRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `SwiperSwipeRoute with non-default startIndex`() {
        val original = SwiperSwipeRoute(sessionId = "session-abc", startIndex = 5)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "sessionId": "session-abc",
                "startIndex": 5
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SwiperSwipeRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `SwiperStatusRoute serialization`() {
        val original = SwiperStatusRoute(sessionId = "session-xyz")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "sessionId": "session-xyz"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SwiperStatusRoute>(serialized)
        deserialized shouldBe original
    }
}
