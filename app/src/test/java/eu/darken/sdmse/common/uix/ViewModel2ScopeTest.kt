package eu.darken.sdmse.common.uix

import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.flow.replayingShare
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import testhelpers.livedata.InstantExecutorExtension

@ExtendWith(InstantExecutorExtension::class)
class ViewModel2ScopeTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `vmScope catches exceptions from replayingShare flows`() = runTest2(
        context = testDispatcher,
    ) {
        val vm = TestViewModel(TestDispatcherProvider(testDispatcher))

        vm.state.first()
        advanceUntilIdle()

        val postedError = vm.errorEvents.value
        postedError.shouldBeInstanceOf<ReadException>()
    }

    private class TestViewModel(
        dispatcherProvider: DispatcherProvider,
    ) : ViewModel3(dispatcherProvider) {
        val state = flow<String> {
            throw ReadException(message = "No matching mode available.")
        }
            .onStart { emit("loading") }
            .replayingShare(vmScope)
    }
}
