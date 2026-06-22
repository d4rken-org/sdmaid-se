package eu.darken.sdmse.analyzer.ui

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson
import java.util.UUID

class AnalyzerRoutesSerializationTest : BaseTest() {

    private val json = Json

    private val testStorageId = StorageId(
        internalId = "primary",
        externalId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
    )

    private val testInstallId = InstallId(
        pkgId = Pkg.Id("com.example.app"),
        userHandle = UserHandle2(0),
    )

    @Test
    fun `DeviceStorageRoute serialization round-trip`() {
        val serialized = json.encodeToString(DeviceStorageRoute.serializer(), DeviceStorageRoute)
        serialized.toComparableKotlinxJson() shouldBe "{}".toComparableKotlinxJson()

        val deserialized = json.decodeFromString(DeviceStorageRoute.serializer(), serialized)
        deserialized shouldBe DeviceStorageRoute
    }

    @Test
    fun `StorageContentRoute serialization round-trip`() {
        val original = StorageContentRoute(storageId = testStorageId)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "storageId": {
                    "internalId": "primary",
                    "externalId": "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<StorageContentRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `AppsRoute serialization round-trip`() {
        val original = AppsRoute(storageId = testStorageId)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "storageId": {
                    "internalId": "primary",
                    "externalId": "550e8400-e29b-41d4-a716-446655440000"
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<AppsRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `AppDetailsRoute serialization round-trip`() {
        val original = AppDetailsRoute(
            storageId = testStorageId,
            installId = testInstallId,
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "storageId": {
                    "internalId": "primary",
                    "externalId": "550e8400-e29b-41d4-a716-446655440000"
                },
                "installId": {
                    "pkgId": {
                        "name": "com.example.app"
                    },
                    "userHandle": {}
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<AppDetailsRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `ContentRoute serialization round-trip`() {
        val original = ContentRoute(
            storageId = testStorageId,
            groupId = ContentGroup.Id(value = "test-group-id"),
            installId = testInstallId,
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "storageId": {
                    "internalId": "primary",
                    "externalId": "550e8400-e29b-41d4-a716-446655440000"
                },
                "groupId": {
                    "value": "test-group-id"
                },
                "installId": {
                    "pkgId": {
                        "name": "com.example.app"
                    },
                    "userHandle": {}
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<ContentRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `ContentRoute with null installId serialization round-trip`() {
        val original = ContentRoute(
            storageId = testStorageId,
            groupId = ContentGroup.Id(value = "test-group-id"),
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "storageId": {
                    "internalId": "primary",
                    "externalId": "550e8400-e29b-41d4-a716-446655440000"
                },
                "groupId": {
                    "value": "test-group-id"
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<ContentRoute>(serialized)
        deserialized shouldBe original
    }
}
