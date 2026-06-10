package eu.darken.sdmse.automation.core

import android.content.ComponentName
import eu.darken.sdmse.common.shell.ShellOps
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AcsActivatorTest : BaseTest() {

    private val ourComp = ComponentName("eu.darken.sdmse", "eu.darken.sdmse.automation.core.AutomationService")
    private val thirdParty = ComponentName("com.eset.etvs.gp", "com.eset.commoncore.core.accessibility.CoreAccessibilityService")
    private val handle = mockk<AutomationServiceHandle>()

    /**
     * Fake [AcsActivator.Io] that models the secure-setting + bind state and records the exact call
     * order, so tests can lock side-effect *timing* (e.g. "mark only after a proven shell bind"), not
     * just final return values.
     */
    private inner class FakeIo(
        val avoidDirect: Boolean = false,
        val mode: ShellOps.Mode? = ShellOps.Mode.ADB,
        val directReverts: Boolean = false,
        val boundAfterDirect: Boolean = false,
        val shellWriteSucceeds: Boolean = true,
        val boundAfterShell: Boolean = true,
    ) : AcsActivator.Io {
        val events = mutableListOf<String>()
        var writeDirectCalls = 0
        var writeShellCalls = 0
        var markCalls = 0
        var lastShellServices: Set<ComponentName>? = null
        var lastAwaitTimeout: Long? = null
        private var bound = false

        override suspend fun shellMode(): ShellOps.Mode? {
            events += "shellMode"
            return mode
        }

        override suspend fun isAvoidDirectWrite(): Boolean {
            events += "isAvoidDirectWrite"
            return avoidDirect
        }

        override suspend fun markDirectWriteUnreliable() {
            events += "mark"
            markCalls++
        }

        override suspend fun writeDirect(services: Set<ComponentName>): Set<ComponentName> {
            events += "writeDirect"
            writeDirectCalls++
            if (!directReverts && boundAfterDirect) bound = true
            return if (directReverts) emptySet() else services
        }

        override suspend fun writeShell(services: Set<ComponentName>, mode: ShellOps.Mode): Boolean {
            events += "writeShell"
            writeShellCalls++
            lastShellServices = services
            if (shellWriteSucceeds && boundAfterShell) bound = true
            return shellWriteSucceeds
        }

        override suspend fun awaitBound(timeoutMs: Long): AutomationServiceHandle? {
            events += "awaitBound"
            lastAwaitTimeout = timeoutMs
            return if (bound) handle else null
        }
    }

    @Test fun `normal device - direct write persists and binds, no shell, no mark`() = runTest2 {
        val io = FakeIo(directReverts = false, boundAfterDirect = true)
        AcsActivator(io).enable(setOf(ourComp)) shouldBe handle
        io.writeShellCalls shouldBe 0
        io.markCalls shouldBe 0
        io.events shouldContainExactly listOf("shellMode", "isAvoidDirectWrite", "writeDirect", "awaitBound")
    }

    @Test fun `mode A - reverted write marks immediately and shell-writes full intended set`() = runTest2 {
        val io = FakeIo(directReverts = true, shellWriteSucceeds = true, boundAfterShell = true)
        val intended = setOf(ourComp, thirdParty)

        AcsActivator(io).enable(intended) shouldBe handle

        io.markCalls shouldBe 1
        io.writeShellCalls shouldBe 1
        // The full original set (incl. the third-party service the wipe took out) is restored.
        io.lastShellServices shouldBe intended
        io.events shouldContainExactly listOf(
            "shellMode", "isAvoidDirectWrite", "writeDirect", "mark", "writeShell", "awaitBound",
        )
    }

    @Test fun `mode B - persisted but no bind falls back to shell and marks only after shell binds`() = runTest2 {
        val io = FakeIo(directReverts = false, boundAfterDirect = false, shellWriteSucceeds = true, boundAfterShell = true)

        AcsActivator(io).enable(setOf(ourComp)) shouldBe handle

        io.writeShellCalls shouldBe 1
        io.markCalls shouldBe 1
        io.events shouldContainExactly listOf(
            "shellMode", "isAvoidDirectWrite", "writeDirect", "awaitBound", "writeShell", "awaitBound", "mark",
        )
    }

    @Test fun `mode B - shell writes but still does not bind returns null and does NOT mark`() = runTest2 {
        val io = FakeIo(directReverts = false, boundAfterDirect = false, shellWriteSucceeds = true, boundAfterShell = false)

        AcsActivator(io).enable(setOf(ourComp)) shouldBe null

        io.markCalls shouldBe 0
        io.events shouldContainExactly listOf(
            "shellMode", "isAvoidDirectWrite", "writeDirect", "awaitBound", "writeShell", "awaitBound",
        )
    }

    @Test fun `mode A - shell write fails returns null but still marked (bounded, no extra awaitBound)`() = runTest2 {
        val io = FakeIo(directReverts = true, shellWriteSucceeds = false)

        AcsActivator(io).enable(setOf(ourComp)) shouldBe null

        io.markCalls shouldBe 1
        io.writeDirectCalls shouldBe 1
        io.writeShellCalls shouldBe 1
        // No awaitBound after a failed shell write.
        io.events shouldContainExactly listOf(
            "shellMode", "isAvoidDirectWrite", "writeDirect", "mark", "writeShell",
        )
    }

    @Test fun `mode A - no shell marks and returns null without shell write`() = runTest2 {
        val io = FakeIo(directReverts = true, mode = null)

        AcsActivator(io).enable(setOf(ourComp)) shouldBe null

        io.markCalls shouldBe 1
        io.writeShellCalls shouldBe 0
        io.events shouldContainExactly listOf("shellMode", "isAvoidDirectWrite", "writeDirect", "mark")
    }

    @Test fun `mode B - no shell returns null and does NOT mark (bind timeout alone is insufficient)`() = runTest2 {
        val io = FakeIo(directReverts = false, boundAfterDirect = false, mode = null)

        AcsActivator(io).enable(setOf(ourComp)) shouldBe null

        io.markCalls shouldBe 0
        io.writeShellCalls shouldBe 0
        io.events shouldContainExactly listOf("shellMode", "isAvoidDirectWrite", "writeDirect", "awaitBound")
    }

    @Test fun `avoid-direct build - skips destructive probe and uses shell`() = runTest2 {
        val io = FakeIo(avoidDirect = true, shellWriteSucceeds = true, boundAfterShell = true)

        AcsActivator(io).enable(setOf(ourComp)) shouldBe handle

        io.writeDirectCalls shouldBe 0
        io.markCalls shouldBe 0
        io.events shouldContainExactly listOf("shellMode", "isAvoidDirectWrite", "writeShell", "awaitBound")
    }

    @Test fun `avoid-direct build - no shell returns null fast without any writes`() = runTest2 {
        val io = FakeIo(avoidDirect = true, mode = null)

        AcsActivator(io).enable(setOf(ourComp)) shouldBe null

        io.writeDirectCalls shouldBe 0
        io.writeShellCalls shouldBe 0
        io.events shouldContainExactly listOf("shellMode", "isAvoidDirectWrite")
    }

    @Test fun `bounded retry - at most one direct and one shell attempt`() = runTest2 {
        val io = FakeIo(directReverts = false, boundAfterDirect = false, shellWriteSucceeds = true, boundAfterShell = true)
        AcsActivator(io).enable(setOf(ourComp)).shouldNotBeNull()
        io.writeDirectCalls shouldBe 1
        io.writeShellCalls shouldBe 1
    }

    @Test fun `bind timeout is passed through to awaitBound`() = runTest2 {
        val io = FakeIo(boundAfterDirect = true)
        AcsActivator(io, bindTimeoutMs = 1234L).enable(setOf(ourComp))
        io.lastAwaitTimeout shouldBe 1234L
    }

    // --- pure helpers ---

    @Test fun `writeMatchesIntent - empty intent matches empty actual`() {
        AcsActivator.writeMatchesIntent(emptySet(), emptySet()) shouldBe true
    }

    @Test fun `writeMatchesIntent - empty intent does not match non-empty actual`() {
        AcsActivator.writeMatchesIntent(emptySet(), setOf(ourComp)) shouldBe false
    }

    @Test fun `writeMatchesIntent - missing component fails`() {
        AcsActivator.writeMatchesIntent(setOf(ourComp), emptySet()) shouldBe false
    }

    @Test fun `writeMatchesIntent - exact match`() {
        AcsActivator.writeMatchesIntent(setOf(ourComp), setOf(ourComp)) shouldBe true
    }

    @Test fun `writeMatchesIntent - superset is success (third-party preserved alongside)`() {
        AcsActivator.writeMatchesIntent(setOf(ourComp), setOf(ourComp, thirdParty)) shouldBe true
    }

    @Test fun `enabledServicesPutCmd - value is single-quoted so the shell will not expand`() {
        // Nested-class component names contain '$' which a shell would expand inside double quotes.
        val value = "com.x/com.x.Svc\$Inner"
        val cmd = AcsActivator.enabledServicesPutCmd(value)
        cmd shouldBe "settings put secure enabled_accessibility_services 'com.x/com.x.Svc\$Inner'"
    }

    @Test fun `flattenServices - joins flattened components with colon`() {
        val flat = AcsActivator.flattenServices(setOf(ourComp, thirdParty))
        flat.split(":").toSet() shouldBe setOf(ourComp.flattenToString(), thirdParty.flattenToString())
    }

    @Test fun `writeStrategy - reliable build writes directly regardless of shell`() {
        AcsActivator.writeStrategy(avoidDirectWrite = false, hasShell = false) shouldBe AcsActivator.WriteStrategy.DIRECT
        AcsActivator.writeStrategy(avoidDirectWrite = false, hasShell = true) shouldBe AcsActivator.WriteStrategy.DIRECT
    }

    @Test fun `writeStrategy - flagged build with shell uses shell`() {
        AcsActivator.writeStrategy(avoidDirectWrite = true, hasShell = true) shouldBe AcsActivator.WriteStrategy.SHELL
    }

    @Test fun `writeStrategy - flagged build without shell SKIPS (never destructive direct write)`() {
        AcsActivator.writeStrategy(avoidDirectWrite = true, hasShell = false) shouldBe AcsActivator.WriteStrategy.SKIP
    }
}
