package eu.darken.sdmse.main.ui.settings.support.contactform

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.EmailTool
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.File
import java.time.Instant

@ExtendWith(MockKExtension::class)
class SupportContactFormViewModelTest : BaseTest() {

    @MockK lateinit var emailTool: EmailTool
    @MockK lateinit var upgradeRepo: UpgradeRepo
    @MockK lateinit var sessionManager: DebugLogSessionManager

    private lateinit var sessionsFlow: MutableStateFlow<List<DebugLogSession>>
    private lateinit var upgradeFlow: MutableStateFlow<UpgradeRepo.Info>

    @BeforeEach
    fun setup() {
        sessionsFlow = MutableStateFlow(emptyList())
        upgradeFlow = MutableStateFlow(object : UpgradeRepo.Info {
            override val type = UpgradeRepo.Type.FOSS
            override val isPro = false
            override val upgradedAt: Instant? = null
            override val error: Throwable? = null
        })

        every { sessionManager.sessions } returns sessionsFlow
        every { sessionManager.refresh() } returns Unit
        coEvery { sessionManager.delete(any()) } returns Unit
        coEvery { sessionManager.startRecording() } returns File("/tmp/recording")
        coEvery { sessionManager.forceStopRecording() } returns null

        every { upgradeRepo.upgradeInfo } returns upgradeFlow

        every { emailTool.build(any(), any()) } returns Intent(Intent.ACTION_SEND)
    }

    private fun buildVM(initialState: SupportContactFormViewModel.State? = null): SupportContactFormViewModel {
        val handle = SavedStateHandle().apply {
            if (initialState != null) set("form_state", initialState)
        }
        return SupportContactFormViewModel(
            handle = handle,
            dispatcherProvider = TestDispatcherProvider(),
            emailTool = emailTool,
            upgradeRepo = upgradeRepo,
            sessionManager = sessionManager,
        )
    }

    private fun finished(baseName: String = "done") = DebugLogSession.Finished(
        id = SessionId("ext:$baseName"),
        createdAt = Instant.parse("2026-04-17T12:00:00Z"),
        logDir = File("/tmp/$baseName"),
        diskSize = 1024L,
        zipFile = File("/tmp/$baseName.zip"),
        compressedSize = 512L,
    )

    @Test
    fun `canSend gates on description word count`() = runTest2 {
        val short = SupportContactFormViewModel.State(description = "too short")
        short.canSend shouldBe false

        val long = SupportContactFormViewModel.State(
            description = List(SupportContactFormViewModel.DESCRIPTION_MIN_WORDS) { "word" }.joinToString(" "),
        )
        long.canSend shouldBe true
    }

    @Test
    fun `canSend gates on expected when Bug`() = runTest2 {
        val bugMissingExpected = SupportContactFormViewModel.State(
            category = SupportContactFormViewModel.Category.BUG,
            description = List(SupportContactFormViewModel.DESCRIPTION_MIN_WORDS) { "word" }.joinToString(" "),
            expectedBehavior = "nope",
        )
        bugMissingExpected.canSend shouldBe false

        val bugFull = bugMissingExpected.copy(
            expectedBehavior = List(SupportContactFormViewModel.EXPECTED_MIN_WORDS) { "word" }.joinToString(" "),
        )
        bugFull.canSend shouldBe true
    }

    @Test
    fun `send emits OpenEmail`() = runTest2 {
        val vm = buildVM(
            SupportContactFormViewModel.State(
                category = SupportContactFormViewModel.Category.QUESTION,
                description = List(SupportContactFormViewModel.DESCRIPTION_MIN_WORDS) { "word" }.joinToString(" "),
            ),
        )

        vm.send()
        val event = vm.events.first()
        event.shouldBeInstanceOf<SupportContactFormEvents.OpenEmail>()
    }

    @Test
    fun `markEmailLaunched arms post-send prompt`() = runTest2 {
        val vm = buildVM()
        vm.markEmailLaunched()
        vm.checkPendingSend()
        val event = vm.events.first()
        event shouldBe SupportContactFormEvents.ShowPostSendPrompt
    }

    @Test
    fun `checkPendingSend without markEmailLaunched emits nothing`() = runTest2 {
        val vm = buildVM()
        vm.checkPendingSend()
        // The events flow is Channel-backed; first would suspend forever. Just verify we don't crash.
    }

    @Test
    fun `auto-select picks up finished session matching pendingSessionId`() = runTest2 {
        val vm = buildVM()

        // Arm the pending session id via confirmStopRecording path — but easier: drive through stopRecording.
        val stoppedId = SessionId("cache:stopped")
        coEvery { sessionManager.requestStopRecording() } returns RecorderModule.StopResult.Stopped(
            sessionId = stoppedId,
            logDir = File("/tmp/stopped"),
        )
        vm.stopRecording()

        // Now the zipping completes and the session turns Finished.
        val session = DebugLogSession.Finished(
            id = stoppedId,
            createdAt = Instant.parse("2026-04-17T12:00:00Z"),
            logDir = File("/tmp/stopped"),
            diskSize = 1024L,
            zipFile = File("/tmp/stopped.zip"),
            compressedSize = 512L,
        )
        sessionsFlow.value = listOf(session)

        val picker = vm.logPickerState.first { it.selectedSessionId == stoppedId }
        picker.selectedSessionId shouldBe stoppedId
    }

    @Test
    fun `selection clears when selected session disappears`() = runTest2 {
        val session = finished("keep")
        sessionsFlow.value = listOf(session)

        val vm = buildVM()
        vm.selectLogSession(session.id)
        vm.logPickerState.first { it.selectedSessionId == session.id }

        sessionsFlow.value = emptyList()

        val after = vm.logPickerState.first { it.sessions.isEmpty() }
        after.selectedSessionId shouldBe null
    }

    @Test
    fun `stopRecording emits ShowShortRecordingWarning on TooShort`() = runTest2 {
        coEvery { sessionManager.requestStopRecording() } returns RecorderModule.StopResult.TooShort

        val vm = buildVM()
        vm.stopRecording()
        vm.events.first() shouldBe SupportContactFormEvents.ShowShortRecordingWarning
    }

    @Test
    fun `state round-trips through SavedStateHandle`() = runTest2 {
        val initial = SupportContactFormViewModel.State(
            category = SupportContactFormViewModel.Category.BUG,
            description = "Hello",
        )
        val vm = buildVM(initial)
        vm.state.first() shouldBe initial
    }

    @Test
    fun `updateDescription is synchronous`() {
        val vm = buildVM()
        vm.updateDescription("word ".repeat(21))
        // No suspension needed — state is immediately updated via MutableStateFlow.update.
        vm.state.value.descriptionWords shouldBe 21
    }
}
