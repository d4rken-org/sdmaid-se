package eu.darken.sdmse.setup

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.darken.sdmse.R
import eu.darken.sdmse.main.ui.MainActivity
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import testhelper.BaseAppFlowTest

/**
 * Each card backed by framework state must be shown while the real framework reports the access missing, and
 * hidden once it reports it granted. Root and Shizuku are settings-driven and not covered. The storage card is
 * covered by [StorageCleanupFlowTest].
 */
@RunWith(AndroidJUnit4::class)
class SetupDetectionTest : BaseAppFlowTest() {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pkg: String get() = context.packageName

    @Before
    fun resetLeftoverAccess() {
        shell("appops set $pkg GET_USAGE_STATS default")
        usageStatsMode() shouldBe AppOpsManager.MODE_DEFAULT
        shell("pm revoke $pkg ${Manifest.permission.WRITE_SECURE_SETTINGS}")
        disableOurAccessibilityService()
    }

    @After
    fun disableOurAccessibilityService() {
        writeEnabledAccessibilityServices(enabledAccessibilityServices().filterNot { it.startsWith("$pkg/") })
    }

    @Test
    fun freshInstallShowsCardsOnlyForApplicableAccess() {
        ActivityScenario.launch(MainActivity::class.java).use {
            walkOnboarding()
            awaitListItem(hasText(str(R.string.setup_usagestats_body)))
            if (Build.VERSION.SDK_INT >= 33) {
                awaitListItem(hasText(str(R.string.setup_notification_body)))
            } else {
                awaitNoListItem(hasText(str(R.string.setup_notification_title)))
            }
            // On 30-32 the SAF card depends on the installed Files app version.
            if (Build.VERSION.SDK_INT < 30) {
                awaitListItem(hasText(str(R.string.setup_saf_card_body)))
            } else if (Build.VERSION.SDK_INT >= 33) {
                awaitNoListItem(hasText(str(R.string.setup_saf_card_title)))
            }
            awaitNoListItem(hasText(str(R.string.setup_inventory_card_title)))
            awaitSetupScreen()
        }
    }

    @Test
    fun grantingUsageStatsHidesTheUsageStatsCard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            walkOnboarding()
            awaitListItem(hasText(str(R.string.setup_usagestats_body)))

            shell("appops set $pkg GET_USAGE_STATS allow")
            usageStatsMode() shouldBe AppOpsManager.MODE_ALLOWED
            scenario.cycleResume()

            awaitNoListItem(hasText(str(R.string.setup_usagestats_title)))
            awaitSetupScreen()
        }
    }

    @Test
    fun grantingNotificationsHidesTheNotificationCard() {
        assumeTrue("POST_NOTIFICATIONS is a runtime permission from API 33", Build.VERSION.SDK_INT >= 33)
        isGranted(Manifest.permission.POST_NOTIFICATIONS) shouldBe false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            walkOnboarding()
            awaitListItem(hasText(str(R.string.setup_notification_body)))

            shell("pm grant $pkg ${Manifest.permission.POST_NOTIFICATIONS}")
            isGranted(Manifest.permission.POST_NOTIFICATIONS) shouldBe true
            scenario.cycleResume()

            awaitNoListItem(hasText(str(R.string.setup_notification_title)))
            awaitSetupScreen()
        }
    }

    @Test
    fun consentingToAccessibilityWithSecureSettingsHidesTheAccessibilityCard() {
        // Must precede launch, or the consent click opens the system accessibility settings and this test fails.
        shell("pm grant $pkg ${Manifest.permission.WRITE_SECURE_SETTINGS}")
        isGranted(Manifest.permission.WRITE_SECURE_SETTINGS) shouldBe true
        ActivityScenario.launch(MainActivity::class.java).use {
            walkOnboarding()
            val consent = hasText(str(R.string.setup_acs_consent_positive_action))
            awaitListItem(consent)
            composeRule.onNode(consent).performScrollTo().performClick()

            awaitNoListItem(hasText(str(R.string.setup_acs_card_title)))
            awaitSetupScreen()
        }
    }

    @Test
    fun enablingTheAccessibilityServiceHidesTheAccessibilityCard() {
        isGranted(Manifest.permission.WRITE_SECURE_SETTINGS) shouldBe false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            walkOnboarding()
            val consent = hasText(str(R.string.setup_acs_consent_positive_action))
            awaitListItem(consent)
            composeRule.onNode(consent).performScrollTo().performClick()
            // Without secure settings, consent sends the user to the system accessibility settings.
            composeRule.waitForIdle()
            pollUntil("MainActivity stopped") { scenario.state == Lifecycle.State.CREATED }

            writeEnabledAccessibilityServices(enabledAccessibilityServices() + "$pkg/$ACS_CLASS")
            pollUntil("accessibility service bound") { isAccessibilityServiceBound() }
            scenario.pressBackUntilResumed()

            awaitNoListItem(hasText(str(R.string.setup_acs_card_title)))
            awaitSetupScreen()
        }
    }

    // Root access stays incomplete throughout, so its card proves the list is still Setup's.
    private fun awaitSetupScreen() = awaitListItem(hasText(str(R.string.setup_root_card_title)))

    private fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun enabledAccessibilityServices(): List<String> =
        shell("settings get secure ${Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES}").trim()
            .takeUnless { it == "null" }
            ?.split(':')
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun writeEnabledAccessibilityServices(services: List<String>) = when {
        services.isEmpty() -> shell("settings delete secure ${Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES}")
        else -> shell("settings put secure ${Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES} ${services.joinToString(":")}")
    }

    // A bound service dumps by label only, e.g. `Service[label=SD Maid SE, feedbackType[], …]`. Ours has no label of
    // its own, so it shows the app label.
    private fun isAccessibilityServiceBound(): Boolean {
        val label = context.applicationInfo.loadLabel(context.packageManager)
        return "Service[label=$label," in shell("dumpsys accessibility")
    }

    @Suppress("DEPRECATION")
    private fun usageStatsMode(): Int = (context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, context.applicationInfo.uid, pkg)

    companion object {
        private const val ACS_CLASS = "eu.darken.sdmse.automation.core.AutomationService"
    }
}
