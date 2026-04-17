package eu.darken.sdmse.main.ui.settings.support.sessions

import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
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
class DebugLogSessionsViewModelTest : BaseTest() {

    @MockK lateinit var sessionManager: DebugLogSessionManager

    private lateinit var sessionsFlow: MutableStateFlow<List<DebugLogSession>>

    @BeforeEach
    fun setup() {
        sessionsFlow = MutableStateFlow(emptyList())
        every { sessionManager.sessions } returns sessionsFlow
        every { sessionManager.refresh() } returns Unit
        coEvery { sessionManager.delete(any()) } returns Unit
        coEvery { sessionManager.deleteAll() } returns Unit
        coEvery { sessionManager.forceStopRecording() } returns null
    }

    private fun buildVM() = DebugLogSessionsViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        sessionManager = sessionManager,
    )

    private fun finished(baseName: String = "done") = DebugLogSession.Finished(
        id = SessionId("ext:$baseName"),
        createdAt = Instant.parse("2026-04-17T12:00:00Z"),
        logDir = File("/tmp/$baseName"),
        diskSize = 1024L,
        zipFile = File("/tmp/$baseName.zip"),
        compressedSize = 512L,
    )

    private fun recording() = DebugLogSession.Recording(
        id = SessionId("cache:rec"),
        createdAt = Instant.parse("2026-04-17T12:00:00Z"),
        logDir = File("/tmp/rec"),
        diskSize = 0L,
    )

    @Test
    fun `state reflects sessionManager flow`() = runTest2 {
        val items = listOf(finished(), recording())
        sessionsFlow.value = items

        val vm = buildVM()
        vm.state.first { it.sessions.isNotEmpty() }.sessions shouldContainExactly items
    }

    @Test
    fun `hasDeletable is false when only recording or zipping`() = runTest2 {
        sessionsFlow.value = listOf(recording())
        val vm = buildVM()
        vm.state.first().hasDeletable shouldBe false
    }

    @Test
    fun `hasDeletable is true when at least one finished or failed`() = runTest2 {
        sessionsFlow.value = listOf(recording(), finished())
        val vm = buildVM()
        vm.state.first { it.sessions.size == 2 }.hasDeletable shouldBe true
    }

    @Test
    fun `openSession emits LaunchRecorder with sessionId`() = runTest2 {
        val vm = buildVM()
        val sessionId = SessionId("ext:abc")
        vm.openSession(sessionId)
        val event = vm.events.first()
        event shouldBe DebugLogSessionsViewModel.Event.LaunchRecorder(sessionId)
    }

    @Test
    fun `delete calls sessionManager`() = runTest2 {
        val vm = buildVM()
        val sessionId = SessionId("ext:abc")
        vm.delete(sessionId)
        coVerify { sessionManager.delete(sessionId) }
    }

    @Test
    fun `deleteAll calls sessionManager`() = runTest2 {
        val vm = buildVM()
        vm.deleteAll()
        coVerify { sessionManager.deleteAll() }
    }

    @Test
    fun `delete failure routes exception to errorEvents`() = runTest2 {
        val failure = IllegalStateException("Cannot delete active session")
        coEvery { sessionManager.delete(any()) } throws failure

        val vm = buildVM()
        vm.delete(SessionId("cache:rec"))
        vm.errorEvents.first() shouldBe failure
    }
}
