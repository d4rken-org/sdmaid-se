package eu.darken.sdmse.common.uix

import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.files.ReadException
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class ViewModel4StateFlowTest : BaseTest() {

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
    fun `safeStateIn forwards failure and keeps fallback state collectable`() = runTest2(
        context = testDispatcher,
    ) {
        val vm = FailingStateViewModel(TestDispatcherProvider(testDispatcher))

        val fallbackState = async { vm.state.first { it == -1 } }
        val forwardedError = async { vm.errorEvents.first() }

        advanceUntilIdle()

        fallbackState.await() shouldBe -1
        forwardedError.await().shouldBeInstanceOf<ReadException>()
        vm.state.value shouldBe -1
        vm.state.first() shouldBe -1
    }

    @Test
    fun `safeStateIn does not convert cancellation into fallback state or error event`() = runTest2(
        context = testDispatcher,
    ) {
        val vm = CancelledStateViewModel(TestDispatcherProvider(testDispatcher))
        var forwardedError: Throwable? = null

        val errorJob = launch {
            vm.errorEvents.collect { forwardedError = it }
        }
        val stateJob = launch {
            vm.state.collect()
        }

        advanceUntilIdle()

        vm.state.value shouldBe 0
        forwardedError.shouldBeNull()

        stateJob.cancel()
        errorJob.cancel()
    }

    private class FailingStateViewModel(
        dispatcherProvider: DispatcherProvider,
    ) : ViewModel4(dispatcherProvider = dispatcherProvider) {
        val state = flow {
            emit(1)
            throw ReadException(message = "No matching mode available.")
        }.safeStateIn(
            initialValue = 0,
            onError = { -1 },
        )
    }

    private class CancelledStateViewModel(
        dispatcherProvider: DispatcherProvider,
    ) : ViewModel4(dispatcherProvider = dispatcherProvider) {
        val state = flow<Int> {
            throw CancellationException("cancelled")
        }.safeStateIn(
            initialValue = 0,
            onError = { -1 },
        )
    }
}
