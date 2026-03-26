package eu.darken.sdmse.setup

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class SetupRouteSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `SetupRoute with null options`() {
        val original = SetupRoute(options = null)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {}
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SetupRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `SetupRoute with populated options`() {
        val original = SetupRoute(
            options = SetupScreenOptions(
                typeFilter = setOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.AUTOMATION),
                isOnboarding = true,
                showCompleted = true,
            )
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "options": {
                    "typeFilter": ["USAGE_STATS", "AUTOMATION"],
                    "isOnboarding": true,
                    "showCompleted": true
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SetupRoute>(serialized)
        deserialized shouldBe original
    }
}
