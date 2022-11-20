package eu.darken.sdmse.main.ui.main

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.main.core.SomeRepo
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelper.BaseTest
import testhelper.coroutine.CoroutinesTestExtension
import testhelper.coroutine.TestDispatcherProvider
import testhelper.livedata.InstantExecutorExtension

@ExtendWith(CoroutinesTestExtension::class, InstantExecutorExtension::class)
class ExampleFragmentVDCTest : BaseTest() {

    @MockK lateinit var someRepo: SomeRepo
    @MockK(relaxUnitFun = true) lateinit var savedStateHandle: SavedStateHandle

    private val alwaysFlow = MutableStateFlow(0L)
    private val whileSubbedFlow = MutableStateFlow(0L)
    private val emojiFlow = MutableStateFlow("\uD83D\uDE02")

    @BeforeEach fun setup() {
        MockKAnnotations.init(this)

        someRepo.apply {
            every { countsAlways } returns alwaysFlow
            every { countsWhileSubscribed } returns whileSubbedFlow
            every { emojis } returns emojiFlow
        }

        savedStateHandle.apply {
            every { keys() } returns emptySet()
            every { this@apply.get<String>(any()) } returns null
        }
    }

    private fun createInstance() = MainFragmentVM(
        handle = savedStateHandle,
        dispatcherProvider = TestDispatcherProvider(),
        someRepo = someRepo,
    )

    @Test
    fun `test state update`() {
        val instance = createInstance()

//        instance.state.observeForever {}
//
//        instance.state.value shouldBe MainFragmentVM.State(data = "WhileSubbed=0 Always=0 \uD83D\uDE02")
//
//        alwaysFlow.value = 42
//
//        instance.state.value shouldBe MainFragmentVM.State(data = "WhileSubbed=0 Always=42 \uD83D\uDE02")
    }
}