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
            "Test APK no longer runs at the app's targetSdk ($EXPECTED_TARGET_SDK); it fell back to " +
                "an AGP default (compileSdk, or minSdk on older AGP). Hidden-API enforcement is keyed " +
                "on the calling app's targetSdk, so reflection results measured here no longer " +
                "represent what the shipped app sees. Check " +
                "`testOptions { targetSdk = projectConfig.targetSdk }` in app-common-io/build.gradle.kts."
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
