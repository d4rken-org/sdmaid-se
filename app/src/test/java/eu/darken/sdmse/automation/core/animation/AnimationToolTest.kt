package eu.darken.sdmse.automation.core.animation

import android.content.Context
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
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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

        every { animationSettings.animationPendingRestoreState } returns pendingRestoreState
    }

    private fun createTool() = AnimationTool(
        context = context,
        adbManager = adbManager,
        rootManager = rootManager,
        shellOps = shellOps,
        animationSettings = animationSettings,
    )

    @Test
    fun `restorePendingState returns false when nothing pending`() = runTest {
        coEvery { pendingRestoreState.value() } returns null

        val tool = createTool()
        tool.restorePendingState() shouldBe false
    }

    @Test
    fun `restorePendingState restores and clears state on success`() = runTest {
        coEvery { pendingRestoreState.value() } returns testState
        coEvery { adbManager.canUseAdbNow() } returns true
        coEvery { rootManager.canUseRootNow() } returns false
        coEvery { shellOps.execute(any<ShellOpsCmd>(), any()) } returns ShellOpsResult(
            exitCode = 0,
            output = emptyList(),
            errors = emptyList(),
        )
        coEvery { pendingRestoreState.update(any()) } returns DataStoreValue.Updated(testState, null)

        val tool = createTool()
        tool.restorePendingState() shouldBe true

        coVerify { shellOps.execute(any<ShellOpsCmd>(), ShellOps.Mode.ADB) }
    }

    @Test
    fun `restorePendingState returns false when canChangeState is false`() = runTest {
        coEvery { pendingRestoreState.value() } returns testState
        coEvery { adbManager.canUseAdbNow() } returns false
        coEvery { rootManager.canUseRootNow() } returns false

        val tool = createTool()
        tool.restorePendingState() shouldBe false

        coVerify(exactly = 0) { shellOps.execute(any<ShellOpsCmd>(), any()) }
    }

    @Test
    fun `restorePendingState catches exception and returns false on failure`() = runTest {
        coEvery { pendingRestoreState.value() } returns testState
        coEvery { adbManager.canUseAdbNow() } returns true
        coEvery { rootManager.canUseRootNow() } returns false
        coEvery { shellOps.execute(any<ShellOpsCmd>(), any()) } throws RuntimeException("Shell failed")

        val tool = createTool()
        tool.restorePendingState() shouldBe false
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
}
