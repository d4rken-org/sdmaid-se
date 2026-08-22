package eu.darken.sdmse.automation.core.animation

import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import android.util.TypedValue
import eu.darken.sdmse.automation.core.AutomationSettings
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.shell.ipc.ShellOpsResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AnimationToolTest : BaseTest() {

    private val context: Context = mockk(relaxed = true)
    private val adbManager: AdbManager = mockk()
    private val rootManager: RootManager = mockk()
    private val shellOps: ShellOps = mockk()
    private val animationSettings: AutomationSettings = mockk()
    private val pendingRestoreState: DataStoreValue<AnimationState?> = mockk()

    private val testState = AnimationState(
        windowAnimationScale = 1.0f,
        globalTransitionAnimationScale = 1.0f,
        globalAnimatorDurationscale = 1.0f,
    )

    @BeforeEach
    fun setup() {
        mockkStatic("eu.darken.sdmse.common.adb.AdbExtensionsKt")
        mockkStatic("eu.darken.sdmse.common.root.RootExtensionsKt")
        mockkStatic("eu.darken.sdmse.common.datastore.DataStoreValueKt")
        mockkStatic(Settings.Global::class)

        every { animationSettings.animationPendingRestoreState } returns pendingRestoreState

        // setState() verifies its writes by reading them back, so every setState path needs these
        stubReadback(1.0f, 1.0f, 1.0f)
    }

    /**
     * Stubs what the system reads back for the three animation scales, `null` meaning the row doesn't exist.
     */
    private fun stubReadback(window: Float?, transition: Float?, animator: Float?) {
        every {
            Settings.Global.getFloat(any(), WINDOW_ANIMATION_SCALE, any())
        } returns (window ?: Float.MIN_VALUE)
        every {
            Settings.Global.getFloat(any(), TRANSITION_ANIMATION_SCALE, any())
        } returns (transition ?: Float.MIN_VALUE)
        every {
            Settings.Global.getFloat(any(), ANIMATOR_DURATION_SCALE, any())
        } returns (animator ?: Float.MIN_VALUE)
    }

    private fun stubShell(exitCode: Int = 0): CapturingSlot<ShellOpsCmd> {
        val cmdSlot = slot<ShellOpsCmd>()
        coEvery { adbManager.canUseAdbNow() } returns true
        coEvery { rootManager.canUseRootNow() } returns false
        coEvery { shellOps.execute(capture(cmdSlot), any()) } returns ShellOpsResult(
            exitCode = exitCode,
            output = emptyList(),
            errors = emptyList(),
        )
        return cmdSlot
    }

    /**
     * Stubs a system where shell writes actually take effect, so [AnimationTool.setState] readback checks pass.
     * [onExecute] receives the 1-based shell call count and can throw to simulate a failing write.
     */
    private fun stubWritableSystem(onExecute: (Int) -> Unit = {}): MutableList<ShellOpsCmd> {
        val current = mutableMapOf(
            WINDOW_ANIMATION_SCALE to 1.0f,
            TRANSITION_ANIMATION_SCALE to 1.0f,
            ANIMATOR_DURATION_SCALE to 1.0f,
        )
        every { Settings.Global.getFloat(any(), any(), any()) } answers {
            current[secondArg<String>()] ?: Float.MIN_VALUE
        }

        val cmds = mutableListOf<ShellOpsCmd>()
        coEvery { adbManager.canUseAdbNow() } returns true
        coEvery { rootManager.canUseRootNow() } returns false
        coEvery { shellOps.execute(capture(cmds), any()) } answers {
            onExecute(cmds.size)
            firstArg<ShellOpsCmd>().cmds.forEach { cmd ->
                val (key, value) = cmd.removePrefix("settings put global ").split(" ")
                current[key] = value.toFloat()
            }
            ShellOpsResult(exitCode = 0, output = emptyList(), errors = emptyList())
        }
        return cmds
    }

    private fun createTool() = AnimationTool(
        context = context,
        adbManager = adbManager,
        rootManager = rootManager,
        shellOps = shellOps,
        animationSettings = animationSettings,
    )

    @Test
    fun `setState with an all-null state writes all three keys`() = runTest {
        val cmdSlot = stubShell()

        val tool = createTool()
        tool.setState(AnimationState(null, null, null))

        cmdSlot.captured.cmds shouldBe listOf(
            "settings put global window_animation_scale 1.0",
            "settings put global transition_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0",
        )
    }

    @Test
    fun `setState writes all three keys for a partially-null state`() = runTest {
        val cmdSlot = stubShell()

        val tool = createTool()
        tool.setState(
            AnimationState(
                windowAnimationScale = 1.0f,
                globalTransitionAnimationScale = 1.0f,
                globalAnimatorDurationscale = null,
            )
        )

        cmdSlot.captured.cmds shouldBe listOf(
            "settings put global window_animation_scale 1.0",
            "settings put global transition_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0",
        )
    }

    @Test
    fun `setState succeeds when the readback matches`() = runTest {
        stubReadback(0.0f, 0.0f, 0.0f)
        stubShell()

        val tool = createTool()
        tool.setState(AnimationState.DISABLED)
    }

    @Test
    fun `setState throws when the readback does not match`() = runTest {
        stubReadback(0.0f, 0.0f, 0.0f)
        stubShell()

        val tool = createTool()
        shouldThrow<IllegalStateException> { tool.setState(testState) }
    }

    @Test
    fun `setState throws when a key is still absent after writing`() = runTest {
        stubReadback(1.0f, 1.0f, null)
        stubShell()

        val tool = createTool()
        shouldThrow<IllegalStateException> { tool.setState(AnimationState(null, null, null)) }
    }

    @Test
    fun `setState succeeds when the shell result is non-zero but the readback matches`() = runTest {
        stubShell(exitCode = 1)

        val tool = createTool()
        tool.setState(testState)
    }

    @Test
    fun `restorePendingState returns NOTHING_PENDING when nothing pending`() = runTest {
        coEvery { pendingRestoreState.value() } returns null

        val tool = createTool()
        tool.restorePendingState() shouldBe AnimationTool.RestoreResult.NOTHING_PENDING
    }

    @Test
    fun `restorePendingState restores and clears state on success`() = runTest {
        coEvery { pendingRestoreState.value() } returns testState
        stubShell()
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(testState, null)

        val tool = createTool()
        tool.restorePendingState() shouldBe AnimationTool.RestoreResult.RESTORED

        coVerify { shellOps.execute(any<ShellOpsCmd>(), ShellOps.Mode.ADB) }
        coVerify { pendingRestoreState.update(any()) }
    }

    @Test
    fun `restorePendingState with an all-null pending state writes all three keys`() = runTest {
        coEvery { pendingRestoreState.value() } returns AnimationState(null, null, null)
        val cmdSlot = stubShell()
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(testState, null)

        val tool = createTool()
        tool.restorePendingState() shouldBe AnimationTool.RestoreResult.RESTORED

        cmdSlot.captured.cmds shouldBe listOf(
            "settings put global window_animation_scale 1.0",
            "settings put global transition_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0",
        )
    }

    @Test
    fun `restorePendingState returns FAILED when canChangeState is false`() = runTest {
        coEvery { pendingRestoreState.value() } returns testState
        coEvery { adbManager.canUseAdbNow() } returns false
        coEvery { rootManager.canUseRootNow() } returns false

        val tool = createTool()
        tool.restorePendingState() shouldBe AnimationTool.RestoreResult.FAILED

        coVerify(exactly = 0) { shellOps.execute(any<ShellOpsCmd>(), any()) }
        coVerify(exactly = 0) { pendingRestoreState.update(any()) }
    }

    @Test
    fun `restorePendingState catches exception and returns FAILED on failure`() = runTest {
        coEvery { pendingRestoreState.value() } returns testState
        coEvery { adbManager.canUseAdbNow() } returns true
        coEvery { rootManager.canUseRootNow() } returns false
        coEvery { shellOps.execute(any<ShellOpsCmd>(), any()) } throws RuntimeException("Shell failed")

        val tool = createTool()
        tool.restorePendingState() shouldBe AnimationTool.RestoreResult.FAILED
    }

    @Test
    fun `restorePendingState does not clear the pending state when the readback fails`() = runTest {
        coEvery { pendingRestoreState.value() } returns testState
        stubReadback(0.0f, 0.0f, 0.0f)
        stubShell()

        val tool = createTool()
        tool.restorePendingState() shouldBe AnimationTool.RestoreResult.FAILED

        coVerify(exactly = 0) { pendingRestoreState.update(any()) }
    }

    @Test
    fun `restorePendingState rethrows CancellationException`() = runTest {
        coEvery { pendingRestoreState.value() } returns testState
        coEvery { adbManager.canUseAdbNow() } returns true
        coEvery { rootManager.canUseRootNow() } returns false
        coEvery { shellOps.execute(any<ShellOpsCmd>(), any()) } throws CancellationException("Cancelled")

        val tool = createTool()
        shouldThrow<CancellationException> { tool.restorePendingState() }

        coVerify(exactly = 0) { pendingRestoreState.update(any()) }
    }

    @Test
    fun `withAnimationsDisabled runs the block with animations off and restores afterwards`() = runTest {
        val cmds = stubWritableSystem()
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(null, testState)

        val tool = createTool()
        var stateDuringBlock: AnimationState? = null
        val result = tool.withAnimationsDisabled {
            stateDuringBlock = tool.getState()
            "block-result"
        }

        result shouldBe "block-result"
        stateDuringBlock shouldBe AnimationState.DISABLED

        cmds.map { it.cmds } shouldBe listOf(
            listOf(
                "settings put global window_animation_scale 0.0",
                "settings put global transition_animation_scale 0.0",
                "settings put global animator_duration_scale 0.0",
            ),
            listOf(
                "settings put global window_animation_scale 1.0",
                "settings put global transition_animation_scale 1.0",
                "settings put global animator_duration_scale 1.0",
            ),
        )
        // Persisted before disabling, cleared after restoring
        coVerify(exactly = 2) { pendingRestoreState.update(any()) }
    }

    @Test
    fun `withAnimationsDisabled restores the previous state when the block throws`() = runTest {
        val cmds = stubWritableSystem()
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(null, testState)

        val tool = createTool()
        shouldThrow<IllegalArgumentException> {
            tool.withAnimationsDisabled<Unit> { throw IllegalArgumentException("Block failed") }
        }

        cmds.last().cmds shouldBe listOf(
            "settings put global window_animation_scale 1.0",
            "settings put global transition_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0",
        )
        coVerify(exactly = 2) { pendingRestoreState.update(any()) }
    }

    @Test
    fun `withAnimationsDisabled runs the block even when the disable write fails`() = runTest {
        stubWritableSystem { callCount -> if (callCount == 1) throw RuntimeException("Shell failed") }
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(null, testState)

        val tool = createTool()
        var blockRan = false
        tool.withAnimationsDisabled { blockRan = true }

        blockRan shouldBe true
        coVerify(exactly = 2) { pendingRestoreState.update(any()) }
    }

    @Test
    fun `withAnimationsDisabled restores the previous state when the disable write is cancelled`() = runTest {
        val cmds = stubWritableSystem { callCount ->
            if (callCount == 1) throw CancellationException("Cancelled")
        }
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(null, testState)

        val tool = createTool()
        var blockRan = false
        shouldThrow<CancellationException> { tool.withAnimationsDisabled { blockRan = true } }

        blockRan shouldBe false
        cmds.last().cmds shouldBe listOf(
            "settings put global window_animation_scale 1.0",
            "settings put global transition_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0",
        )
        coVerify(exactly = 2) { pendingRestoreState.update(any()) }
    }

    @Test
    fun `withAnimationsDisabled propagates a persist failure and does not disable`() = runTest {
        coEvery { pendingRestoreState.update(any()) } throws RuntimeException("DataStore failed")

        val tool = createTool()
        var blockRan = false
        shouldThrow<RuntimeException> { tool.withAnimationsDisabled { blockRan = true } }

        blockRan shouldBe false
        coVerify(exactly = 0) { shellOps.execute(any<ShellOpsCmd>(), any()) }
    }

    @Test
    fun `withAnimationsDisabled does not clear the pending state when the restore write fails`() = runTest {
        stubWritableSystem { callCount -> if (callCount == 2) throw RuntimeException("Shell lost") }
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(null, testState)

        val tool = createTool()
        tool.withAnimationsDisabled { }

        // Only the initial persist, the pending record has to survive a failed restore
        coVerify(exactly = 1) { pendingRestoreState.update(any()) }
    }

    @Test
    fun `withAnimationsDisabled blocks a concurrent restore until the block is done`() = runTest {
        stubWritableSystem()
        coEvery { pendingRestoreState.value() } returns testState
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(null, testState)

        val tool = createTool()
        val blockGate = CompletableDeferred<Unit>()
        val txJob = launch { tool.withAnimationsDisabled { blockGate.await() } }
        runCurrent()

        var restoreResult: AnimationTool.RestoreResult? = null
        val restoreJob = launch { restoreResult = tool.restorePendingState() }
        runCurrent()

        // The transaction still holds the lock, so the restore can't re-enable animations or clear the record yet
        restoreJob.isCompleted shouldBe false
        restoreResult shouldBe null

        blockGate.complete(Unit)
        txJob.join()
        restoreJob.join()

        restoreResult shouldBe AnimationTool.RestoreResult.RESTORED
    }

    @Test
    fun `defaultTransitionScale falls back when the framework resource is not a float`() = runTest {
        val cmdSlot = stubShell()
        mockkStatic(Resources::class)
        val systemResources: Resources = mockk()
        every { Resources.getSystem() } returns systemResources
        every { systemResources.getIdentifier(any(), any(), any()) } returns 42
        every { systemResources.getValue(any<Int>(), any(), any()) } answers {
            secondArg<TypedValue>().type = TypedValue.TYPE_DIMENSION
        }

        try {
            val tool = createTool()
            tool.setState(
                AnimationState(
                    windowAnimationScale = 1.0f,
                    globalTransitionAnimationScale = null,
                    globalAnimatorDurationscale = 1.0f,
                )
            )
        } finally {
            unmockkStatic(Resources::class)
        }

        cmdSlot.captured.cmds shouldBe listOf(
            "settings put global window_animation_scale 1.0",
            "settings put global transition_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0",
        )
    }

    @Test
    fun `persistPendingState saves state to settings`() = runTest {
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(null, testState)

        val tool = createTool()
        tool.persistPendingState(testState)

        coVerify { pendingRestoreState.update(any()) }
    }

    @Test
    fun `clearPendingState clears settings`() = runTest {
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(testState, null)

        val tool = createTool()
        tool.clearPendingState()

        coVerify { pendingRestoreState.update(any()) }
    }

    companion object {
        private const val WINDOW_ANIMATION_SCALE = "window_animation_scale"
        private const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
        private const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
    }
}
