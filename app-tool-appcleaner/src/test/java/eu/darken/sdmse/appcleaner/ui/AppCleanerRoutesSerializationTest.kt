package eu.darken.sdmse.appcleaner.ui

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class AppCleanerRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `AppJunkDetailsRoute with null identifier`() {
        val original = AppJunkDetailsRoute(identifier = null)

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {}
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<AppJunkDetailsRoute>(serialized)
        deserialized shouldBe original
    }

    @Test
    fun `AppJunkDetailsRoute with non-null identifier`() {
        val original = AppJunkDetailsRoute(
            identifier = InstallId(
                pkgId = Pkg.Id("com.example.app"),
                userHandle = UserHandle2(0),
            )
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "identifier": {
                    "pkgId": {
                        "name": "com.example.app"
                    },
                    "userHandle": {}
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<AppJunkDetailsRoute>(serialized)
        deserialized shouldBe original
    }

}
