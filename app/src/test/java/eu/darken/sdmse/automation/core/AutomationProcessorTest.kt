package eu.darken.sdmse.automation.core

import eu.darken.sdmse.automation.core.animation.AnimationState
import eu.darken.sdmse.automation.core.animation.AnimationTool
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider

class AutomationProcessorTest : BaseTest() {

    private val automationHost: AutomationHost = mockk(relaxed = true)
    private val animationTool: AnimationTool = mockk()
    private val moduleFactory: AutomationModule.Factory = mockk()
    private val module: AutomationModule = mockk()
    private val task: AutomationTask = mockk()
    private val taskResult: AutomationTask.Result = mockk()

    private val originalState = AnimationState(
        windowAnimationScale = 1.0f,
        globalTransitionAnimationScale = 1.0f,
        globalAnimatorDurationscale = 1.0f,
    )

    @BeforeEach
    fun setup() {
        coEvery { animationTool.restorePendingState() } returns AnimationTool.RestoreResult.NOTHING_PENDING
        coEvery { animationTool.canChangeState() } returns true
        coEvery { animationTool.captureAndDisable() } returns originalState
        coEvery { animationTool.getState() } returns originalState
        coEvery { animationTool.setState(any()) } returns Unit
        coEvery { animationTool.clearPendingState() } returns Unit

        coEvery { moduleFactory.isResponsible(any()) } returns true
        coEvery { moduleFactory.create(any(), any()) } returns module
        coEvery { module.process(any()) } returns taskResult
    }

    private fun createProcessor() = AutomationProcessor(
        automationHost = automationHost,
        dispatcherProvider = TestDispatcherProvider(),
        moduleFactories = setOf(moduleFactory),
        animationTool = animationTool,
    )

    @Test
    fun `animations are disabled before processing and restored after`() = runTest {
        val processor = createProcessor()
        val result = processor.process(task)

        result shouldBe taskResult

        coVerifyOrder {
            animationTool.restorePendingState()
            animationTool.canChangeState()
            animationTool.captureAndDisable()
            module.process(task)
            animationTool.setState(originalState)
            animationTool.clearPendingState()
        }
    }

    @Test
    fun `animations are restored even when task fails`() = runTest {
        coEvery { module.process(any()) } throws RuntimeException("Task failed")

        val processor = createProcessor()
        shouldThrow<RuntimeException> { processor.process(task) }

        coVerify {
            animationTool.captureAndDisable()
            animationTool.setState(originalState)
            animationTool.clearPendingState()
        }
    }

    @Test
    fun `animations not touched when canChangeState is false`() = runTest {
        coEvery { animationTool.canChangeState() } returns false

        val processor = createProcessor()
        processor.process(task)

        coVerify(exactly = 0) { animationTool.captureAndDisable() }
        coVerify(exactly = 0) { animationTool.setState(any()) }
        coVerify(exactly = 0) { animationTool.clearPendingState() }
    }

    @Test
    fun `clearPendingState not called when restore fails`() = runTest {
        coEvery { animationTool.setState(originalState) } throws RuntimeException("Shell lost")

        val processor = createProcessor()
        processor.process(task)

        coVerify { animationTool.setState(originalState) }
        coVerify(exactly = 0) { animationTool.clearPendingState() }
    }

    @Test
    fun `does not capture or disable when the restore failed`() = runTest {
        coEvery { animationTool.restorePendingState() } returns AnimationTool.RestoreResult.FAILED

        val processor = createProcessor()
        val result = processor.process(task)

        result shouldBe taskResult
        coVerify(exactly = 0) { animationTool.captureAndDisable() }
        coVerify(exactly = 0) { animationTool.setState(any()) }
        coVerify(exactly = 0) { animationTool.clearPendingState() }
    }

    @Test
    fun `RESTORED allows the normal disable path`() = runTest {
        coEvery { animationTool.restorePendingState() } returns AnimationTool.RestoreResult.RESTORED

        val processor = createProcessor()
        processor.process(task)

        coVerify { animationTool.captureAndDisable() }
        coVerify { animationTool.setState(originalState) }
    }

    @Test
    fun `restorePendingState failure does not block processing`() = runTest {
        coEvery { animationTool.restorePendingState() } throws RuntimeException("DataStore corrupted")

        val processor = createProcessor()
        val result = processor.process(task)

        result shouldBe taskResult
        coVerify { module.process(task) }
        coVerify(exactly = 0) { animationTool.captureAndDisable() }
        coVerify(exactly = 0) { animationTool.setState(any()) }
    }

    @Test
    fun `disable failure does not fail the task`() = runTest {
        // captureAndDisable swallows a failed disable internally and still returns the captured state
        coEvery { animationTool.captureAndDisable() } returns originalState

        val processor = createProcessor()
        val result = processor.process(task)

        result shouldBe taskResult
        coVerify { animationTool.setState(originalState) }
        coVerify { animationTool.clearPendingState() }
    }

    @Test
    fun `hasTask is false after processing completes`() = runTest {
        val processor = createProcessor()
        processor.hasTask shouldBe false

        processor.process(task)

        processor.hasTask shouldBe false
    }

    @Test
    fun `hasTask is false after processing fails`() = runTest {
        coEvery { module.process(any()) } throws RuntimeException("Failed")

        val processor = createProcessor()
        shouldThrow<RuntimeException> { processor.process(task) }

        processor.hasTask shouldBe false
    }

    @Test
    fun `hasTask is reset when module resolution fails`() = runTest {
        coEvery { moduleFactory.isResponsible(any()) } returns false

        val processor = createProcessor()
        shouldThrow<IllegalStateException> { processor.process(task) }

        processor.hasTask shouldBe false
    }
}
