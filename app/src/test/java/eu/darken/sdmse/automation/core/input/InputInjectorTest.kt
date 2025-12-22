package eu.darken.sdmse.automation.core.input

import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.shell.ipc.ShellOpsResult
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class InputInjectorTest : BaseTest() {

    private val adbManager: AdbManager = mockk()
    private val rootManager: RootManager = mockk()
    private val shellOps: ShellOps = mockk()

    private val useAdbFlow = MutableStateFlow(false)
    private val useRootFlow = MutableStateFlow(false)

    private lateinit var injector: InputInjector

    @BeforeEach
    fun setup() {
        every { adbManager.useAdb } returns useAdbFlow
        every { rootManager.useRoot } returns useRootFlow
        coEvery { shellOps.execute(any(), any()) } returns ShellOpsResult(0, emptyList(), emptyList())

        injector = InputInjector(
            adbManager = adbManager,
            rootManager = rootManager,
            shellOps = shellOps,
        )
    }

    @Test
    fun `canInject returns true when ADB available`() = runTest {
        useAdbFlow.value = true
        useRootFlow.value = false

        injector.canInject() shouldBe true
    }

    @Test
    fun `canInject returns true when Root available`() = runTest {
        useAdbFlow.value = false
        useRootFlow.value = true

        injector.canInject() shouldBe true
    }

    @Test
    fun `canInject returns false when neither ADB nor Root available`() = runTest {
        useAdbFlow.value = false
        useRootFlow.value = false

        injector.canInject() shouldBe false
    }

    @Test
    fun `inject executes correct shell command for DpadRight`() = runTest {
        useAdbFlow.value = true

        val cmdSlot = slot<ShellOpsCmd>()
        coEvery { shellOps.execute(capture(cmdSlot), any()) } returns ShellOpsResult(0, emptyList(), emptyList())

        injector.inject(InputInjector.Event.DpadRight)

        cmdSlot.captured.cmds shouldBe listOf("input keyevent 22")
    }

    @Test
    fun `inject executes correct shell command for DpadCenter`() = runTest {
        useAdbFlow.value = true

        val cmdSlot = slot<ShellOpsCmd>()
        coEvery { shellOps.execute(capture(cmdSlot), any()) } returns ShellOpsResult(0, emptyList(), emptyList())

        injector.inject(InputInjector.Event.DpadCenter)

        cmdSlot.captured.cmds shouldBe listOf("input keyevent 23")
    }

    @Test
    fun `inject uses ADB mode when available`() = runTest {
        useAdbFlow.value = true
        useRootFlow.value = false

        injector.inject(InputInjector.Event.DpadRight)

        coVerify { shellOps.execute(any(), ShellOps.Mode.ADB) }
    }

    @Test
    fun `inject uses ROOT mode when ADB not available`() = runTest {
        useAdbFlow.value = false
        useRootFlow.value = true

        injector.inject(InputInjector.Event.DpadRight)

        coVerify { shellOps.execute(any(), ShellOps.Mode.ROOT) }
    }

    @Test
    fun `inject vararg executes all events in order`() = runTest {
        useAdbFlow.value = true

        val cmds = mutableListOf<String>()
        coEvery { shellOps.execute(any(), any()) } answers {
            cmds.add(firstArg<ShellOpsCmd>().cmds.first())
            ShellOpsResult(0, emptyList(), emptyList())
        }

        injector.inject(
            InputInjector.Event.DpadRight,
            InputInjector.Event.DpadRight,
            InputInjector.Event.DpadCenter,
        )

        cmds shouldBe listOf(
            "input keyevent 22",
            "input keyevent 22",
            "input keyevent 23",
        )
    }

    @Test
    fun `tap executes correct shell command`() = runTest {
        useAdbFlow.value = true

        val cmdSlot = slot<ShellOpsCmd>()
        coEvery { shellOps.execute(capture(cmdSlot), any()) } returns ShellOpsResult(0, emptyList(), emptyList())

        injector.tap(500, 750)

        cmdSlot.captured.cmds shouldBe listOf("input tap 500 750")
    }

    @Test
    fun `tap uses ADB mode when available`() = runTest {
        useAdbFlow.value = true
        useRootFlow.value = false

        injector.tap(100, 200)

        coVerify { shellOps.execute(any(), ShellOps.Mode.ADB) }
    }

    @Test
    fun `tap uses ROOT mode when ADB not available`() = runTest {
        useAdbFlow.value = false
        useRootFlow.value = true

        injector.tap(100, 200)

        coVerify { shellOps.execute(any(), ShellOps.Mode.ROOT) }
    }
}