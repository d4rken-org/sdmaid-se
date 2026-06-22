package eu.darken.sdmse.deduplicator.ui

import eu.darken.sdmse.deduplicator.core.Duplicate
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class DeduplicatorRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `DeduplicatorDetailsRoute with non-null identifier serialization round-trip`() {
        val original = DeduplicatorDetailsRoute(
            identifier = Duplicate.Cluster.Id(value = "cluster-abc-123"),
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "identifier": {
                    "value": "cluster-abc-123"
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<DeduplicatorDetailsRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `DeduplicatorDetailsRoute with null identifier serialization round-trip`() {
        val original = DeduplicatorDetailsRoute(identifier = null)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {}
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<DeduplicatorDetailsRoute>(serialized)
        deserialized shouldBe original
    }

}
