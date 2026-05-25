package eu.darken.sdmse.automation.core.specs

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.errors.AutomationInterferenceException
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SettingsInterferenceDetectorTest : BaseTest() {

    private val settingsPkg = "com.android.settings".toPkgId()
    private val targetPkg = "com.example.target".toPkgId()
    private val ownPkg = "eu.darken.sdmse"
    private val knownBlocker = "com.avast.android.mobilesecurity".toPkgId()
    private val foreignPkg = "com.some.thirdparty".toPkgId()

    private var clock = 1000L

    private fun rootOf(pkg: String?): ACSNodeInfo = mockk {
        every { packageName } returns pkg
    }

    private fun create(
        systemApps: Set<String> = emptySet(),
    ) = SettingsInterferenceDetector(
        expectedPkgs = setOf(settingsPkg),
        targetPkg = targetPkg,
        nowMs = { clock },
        resolveLabel = { "App Label" },
        isSystemApp = { pkg -> pkg.name in systemApps },
    )

    @Test
    fun `known blocker throws immediately`() = runTest {
        val detector = create()
        shouldThrow<AutomationInterferenceException> {
            detector.evaluate(rootOf(knownBlocker.name), ownPkg)
        }.blockerPkg shouldBe knownBlocker
    }

    @Test
    fun `known blocker throws even when it is the target app`() = runTest {
        val detector = SettingsInterferenceDetector(
            expectedPkgs = setOf(settingsPkg),
            targetPkg = knownBlocker,
            nowMs = { clock },
            resolveLabel = { null },
            isSystemApp = { false },
        )
        shouldThrow<AutomationInterferenceException> {
            detector.evaluate(rootOf(knownBlocker.name), ownPkg)
        }
    }

    @Test
    fun `expected settings window does not trigger`() = runTest {
        val detector = create()
        detector.evaluate(rootOf(settingsPkg.name), ownPkg)
    }

    @Test
    fun `our own window does not trigger`() = runTest {
        val detector = create()
        detector.evaluate(rootOf(ownPkg), ownPkg)
    }

    @Test
    fun `target app window does not trigger generic detection`() = runTest {
        val detector = create()
        // Even seen persistently, the app being cleaned is expected to own the window briefly.
        detector.evaluate(rootOf(targetPkg.name), ownPkg)
        clock += 5000
        detector.evaluate(rootOf(targetPkg.name), ownPkg)
    }

    @Test
    fun `system app window does not trigger`() = runTest {
        val detector = create(systemApps = setOf(foreignPkg.name))
        detector.evaluate(rootOf(foreignPkg.name), ownPkg)
        clock += 5000
        detector.evaluate(rootOf(foreignPkg.name), ownPkg)
    }

    @Test
    fun `null or blank package name is ignored`() = runTest {
        val detector = create()
        detector.evaluate(rootOf(null), ownPkg)
        detector.evaluate(rootOf(""), ownPkg)
        detector.evaluate(rootOf("   "), ownPkg)
    }

    @Test
    fun `foreign non-system app throws only after persisting`() = runTest {
        val detector = create()
        // First sighting just starts the timer.
        detector.evaluate(rootOf(foreignPkg.name), ownPkg)
        // Still within the threshold -> no throw.
        clock += 1000
        detector.evaluate(rootOf(foreignPkg.name), ownPkg)
        // Past the threshold -> throw.
        clock += 600
        shouldThrow<AutomationInterferenceException> {
            detector.evaluate(rootOf(foreignPkg.name), ownPkg)
        }.blockerPkg shouldBe foreignPkg
    }

    @Test
    fun `switching foreign app resets the persistence timer`() = runTest {
        val detector = create()
        val otherForeign = "com.other.thirdparty"
        detector.evaluate(rootOf(foreignPkg.name), ownPkg)
        clock += 2000
        // A different foreign app appears -> timer restarts, so this must NOT throw.
        detector.evaluate(rootOf(otherForeign), ownPkg)
        // Just under the threshold for the new app -> still no throw.
        clock += 1400
        detector.evaluate(rootOf(otherForeign), ownPkg)
    }

    @Test
    fun `benign window between sightings resets the timer`() = runTest {
        val detector = create()
        detector.evaluate(rootOf(foreignPkg.name), ownPkg)
        clock += 2000
        // Settings briefly shows up -> resets.
        detector.evaluate(rootOf(settingsPkg.name), ownPkg)
        // Foreign reappears: timer restarts, under threshold -> no throw.
        detector.evaluate(rootOf(foreignPkg.name), ownPkg)
        clock += 1400
        detector.evaluate(rootOf(foreignPkg.name), ownPkg)
    }
}
