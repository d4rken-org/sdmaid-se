package eu.darken.sdmse.stats.ui

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson
import java.util.UUID

class StatsRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `SpaceHistoryRoute with null storageId`() {
        val original = SpaceHistoryRoute(storageId = null)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {}
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SpaceHistoryRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `SpaceHistoryRoute with non-null storageId`() {
        val original = SpaceHistoryRoute(storageId = "primary")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "storageId": "primary"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<SpaceHistoryRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `AffectedFilesRoute with string reportId`() {
        val original = AffectedFilesRoute(reportId = "550e8400-e29b-41d4-a716-446655440000")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "reportId": "550e8400-e29b-41d4-a716-446655440000"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<AffectedFilesRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `AffectedFilesRoute UUID round-trip`() {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val original = AffectedFilesRoute(uuid)

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<AffectedFilesRoute>(serialized)

        deserialized shouldBe original
        deserialized.reportIdUUID shouldBe uuid
    }

    @Test
    fun `AffectedPkgsRoute with string reportId`() {
        val original = AffectedPkgsRoute(reportId = "660e8400-e29b-41d4-a716-446655440000")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "reportId": "660e8400-e29b-41d4-a716-446655440000"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<AffectedPkgsRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `AffectedPkgsRoute UUID round-trip`() {
        val uuid = UUID.fromString("660e8400-e29b-41d4-a716-446655440000")
        val original = AffectedPkgsRoute(uuid)

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<AffectedPkgsRoute>(serialized)

        deserialized shouldBe original
        deserialized.reportIdUUID shouldBe uuid
    }
}
