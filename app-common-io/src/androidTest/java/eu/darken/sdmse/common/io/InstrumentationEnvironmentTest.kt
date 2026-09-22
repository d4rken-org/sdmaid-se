package eu.darken.sdmse.common.io

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentationEnvironmentTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testApkRunsAtAppTargetSdk() {
        withClue(
            "Test APK does not run at the expected targetSdk ($EXPECTED_TARGET_SDK). If " +
                "ProjectConfig.targetSdk was bumped, update EXPECTED_TARGET_SDK in this file and add the " +
                "new level to the `api` matrix in .github/workflows/emulator.yml. Otherwise the test APK " +
                "fell back to an AGP default, so check " +
                "`testOptions { targetSdk = projectConfig.targetSdk }` in app-common-io/build.gradle.kts: " +
                "hidden-API results are only representative at the app's own target."
        ) {
            targetContext.applicationInfo.targetSdkVersion shouldBe EXPECTED_TARGET_SDK
        }
    }

    @Test
    fun instrumentationTargetsThisModule() {
        targetContext.packageName shouldBe "eu.darken.sdmse.common.io.test"
    }

    companion object {
        // Must track ProjectConfig.targetSdk; drifting apart is the failure this class exists to catch.
        private const val EXPECTED_TARGET_SDK = 36
    }
}
