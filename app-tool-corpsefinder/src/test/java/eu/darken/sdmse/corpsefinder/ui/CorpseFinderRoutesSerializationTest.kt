package eu.darken.sdmse.corpsefinder.ui

import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class CorpseFinderRoutesSerializationTest : BaseTest() {

    private val json = Json

    private val testPath = LocalPath.build("/storage/emulated/0/Android/data/com.test")

    @Test
    fun `CorpseDetailsRoute serialization round-trip`() {
        val original = CorpseDetailsRoute(corpsePath = testPath)

        original.corpsePathJson shouldNotBe null

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<CorpseDetailsRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `CorpseDetailsRoute APath convenience constructor and getter`() {
        val route = CorpseDetailsRoute(corpsePath = testPath)

        route.corpsePath shouldBe testPath
    }

    @Test
    fun `CorpseDetailsRoute with null corpsePath serialization round-trip`() {
        val original = CorpseDetailsRoute(corpsePath = null)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {}
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<CorpseDetailsRoute>(serialized)
        deserialized shouldBe original
        deserialized.corpsePath shouldBe null
    }
}
