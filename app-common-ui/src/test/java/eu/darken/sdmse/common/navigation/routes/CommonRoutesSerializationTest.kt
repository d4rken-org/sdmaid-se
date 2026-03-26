package eu.darken.sdmse.common.navigation.routes

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class CommonRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `UpgradeRoute with forced true`() {
        val original = UpgradeRoute(forced = true)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "forced": true
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<UpgradeRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `UpgradeRoute with default value`() {
        val original = UpgradeRoute()

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {}
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<UpgradeRoute>(serialized)
        deserialized shouldBe original
    }
}
