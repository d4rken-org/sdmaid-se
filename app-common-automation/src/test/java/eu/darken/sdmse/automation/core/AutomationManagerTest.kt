package eu.darken.sdmse.automation.core

import android.content.ComponentName
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutomationManagerTest : BaseTest() {

    private val ourComp = ComponentName("eu.darken.sdmse", "eu.darken.sdmse.automation.core.AutomationService")
    private val otherComp = ComponentName("com.other.app", "com.other.app.SomeService")

    @Test fun `parseAccessibilityTargets - both null returns empty`() {
        AutomationManager.parseAccessibilityTargets(null, null) shouldBe emptySet()
    }

    @Test fun `parseAccessibilityTargets - single null returns empty`() {
        AutomationManager.parseAccessibilityTargets(null) shouldBe emptySet()
    }

    @Test fun `parseAccessibilityTargets - shortcut setting contains our component`() {
        val result = AutomationManager.parseAccessibilityTargets(ourComp.flattenToString(), null)
        result shouldBe setOf(ourComp)
    }

    @Test fun `parseAccessibilityTargets - button setting contains our component`() {
        val result = AutomationManager.parseAccessibilityTargets(null, ourComp.flattenToString())
        result shouldBe setOf(ourComp)
    }

    @Test fun `parseAccessibilityTargets - both settings contain our component`() {
        val result = AutomationManager.parseAccessibilityTargets(
            ourComp.flattenToString(),
            ourComp.flattenToString(),
        )
        result shouldBe setOf(ourComp)
    }

    @Test fun `parseAccessibilityTargets - multi-entry list contains our component`() {
        val setting = "${otherComp.flattenToString()}:${ourComp.flattenToString()}"
        val result = AutomationManager.parseAccessibilityTargets(setting, null)
        result shouldBe setOf(otherComp, ourComp)
    }

    @Test fun `parseAccessibilityTargets - neither setting contains our component`() {
        val result = AutomationManager.parseAccessibilityTargets(otherComp.flattenToString(), null)
        (ourComp in result) shouldBe false
    }

    @Test fun `parseAccessibilityTargets - blank entries are filtered`() {
        val setting = ":${ourComp.flattenToString()}:"
        val result = AutomationManager.parseAccessibilityTargets(setting)
        result shouldBe setOf(ourComp)
    }

    @Test fun `parseAccessibilityTargets - malformed entry (no slash) is skipped, valid entry is kept`() {
        // ComponentName.unflattenFromString returns null when there is no '/'
        val setting = "notvalid:${ourComp.flattenToString()}"
        val result = AutomationManager.parseAccessibilityTargets(setting)
        result shouldBe setOf(ourComp)
    }

    @Test fun `parseAccessibilityTargets - empty string returns empty`() {
        AutomationManager.parseAccessibilityTargets("") shouldBe emptySet()
    }
}
