package eu.darken.sdmse.common.device

import com.squareup.moshi.Moshi
import eu.darken.sdmse.common.serialization.SerializationCommonModule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RomTypeTest : BaseTest() {

    private val moshi: Moshi = SerializationCommonModule().moshi()
    private val adapter = moshi.adapter(RomType::class.java)

    @Test
    fun `serialization uses Json name annotation`() {
        val romType = RomType.FUNTOUCHOS
        val json = adapter.toJson(romType)
        json shouldBe "\"VIVO\""
    }

    @Test
    fun `deserialization uses Json name annotation`() {
        val json = "\"VIVO\""
        val romType = adapter.fromJson(json)
        romType shouldBe RomType.FUNTOUCHOS
    }

    @Test
    fun `VIVO maps to FUNTOUCHOS enum constant`() {
        val json = "\"VIVO\""
        val deserialized = adapter.fromJson(json)
        deserialized shouldBe RomType.FUNTOUCHOS
        deserialized?.name shouldBe "FUNTOUCHOS"
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

        testCases.forEach { (json, expectedEnum) ->
            val deserialized = adapter.fromJson(json)
            deserialized shouldBe expectedEnum

            val serialized = adapter.toJson(expectedEnum)
            serialized shouldBe json
        }
    }
}
