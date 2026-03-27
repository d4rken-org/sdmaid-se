package eu.darken.sdmse.common.device

import eu.darken.sdmse.common.serialization.SerializationCommonModule
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RomTypeTest : BaseTest() {

    private val json: Json = SerializationCommonModule().json()

    @Test
    fun `serialization uses SerialName annotation`() {
        val romType = RomType.FUNTOUCHOS
        val jsonStr = json.encodeToString(RomType.serializer(), romType)
        jsonStr shouldBe "\"VIVO\""
    }

    @Test
    fun `deserialization uses SerialName annotation`() {
        val jsonStr = "\"VIVO\""
        val romType = json.decodeFromString(RomType.serializer(), jsonStr)
        romType shouldBe RomType.FUNTOUCHOS
    }

    @Test
    fun `VIVO maps to FUNTOUCHOS enum constant`() {
        val jsonStr = "\"VIVO\""
        val deserialized = json.decodeFromString(RomType.serializer(), jsonStr)
        deserialized shouldBe RomType.FUNTOUCHOS
        deserialized.name shouldBe "FUNTOUCHOS"
    }

    @Test
    fun `other enum values work correctly`() {
        val testCases = mapOf(
            "\"AUTO\"" to RomType.AUTO,
            "\"SAMSUNG\"" to RomType.ONEUI,
            "\"MIUI\"" to RomType.MIUI,
            "\"ONEPLUS\"" to RomType.OXYGENOS,
            "\"HONOR\"" to RomType.HONOR
        )

        testCases.forEach { (jsonStr, expectedEnum) ->
            val deserialized = json.decodeFromString(RomType.serializer(), jsonStr)
            deserialized shouldBe expectedEnum

            val serialized = json.encodeToString(RomType.serializer(), expectedEnum)
            serialized shouldBe jsonStr
        }
    }
}
