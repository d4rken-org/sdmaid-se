package eu.darken.sdmse.appcontrol.ui

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class AppControlRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `AppActionRoute serialization`() {
        val original = AppActionRoute(
            installId = InstallId(
                pkgId = Pkg.Id("com.example.app"),
                userHandle = UserHandle2(0),
            )
        )

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "installId": {
                    "pkgId": {
                        "name": "com.example.app"
                    },
                    "userHandle": {}
                }
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<AppActionRoute>(serialized)
        deserialized shouldBe original
    }
}
