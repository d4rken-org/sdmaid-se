package eu.darken.sdmse.systemcleaner.ui

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class SystemCleanerRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `FilterContentDetailsRoute with null filterIdentifier`() {
        val original = FilterContentDetailsRoute(filterIdentifier = null)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {}
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<FilterContentDetailsRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `FilterContentDetailsRoute with non-null filterIdentifier`() {
        val original = FilterContentDetailsRoute(filterIdentifier = "eu.darken.sdmse.systemcleaner.filter.AdvertisementFilter")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "filterIdentifier": "eu.darken.sdmse.systemcleaner.filter.AdvertisementFilter"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<FilterContentDetailsRoute>(serialized)
        deserialized shouldBe original
    }

}
