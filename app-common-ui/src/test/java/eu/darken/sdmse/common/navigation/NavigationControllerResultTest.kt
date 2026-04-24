package eu.darken.sdmse.common.navigation

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NavigationControllerResultTest : BaseTest() {

    private object TestStringKey : ResultKey<String> {
        override val name: String = "test.string"
    }

    private object TestIntKey : ResultKey<Int> {
        override val name: String = "test.int"
    }

    @Test
    fun `setResult then resultFlow emits the value`() = runTest {
        val ctrl = NavigationController()

        ctrl.setResult(TestStringKey, "hello")

        ctrl.resultFlow(TestStringKey).filterNotNull().first() shouldBe "hello"
    }

    @Test
    fun `consumeResult clears the stored value`() = runTest {
        val ctrl = NavigationController()

        ctrl.setResult(TestStringKey, "hello")
        ctrl.resultFlow(TestStringKey).filterNotNull().first() shouldBe "hello"

        ctrl.consumeResult(TestStringKey)
        ctrl.resultFlow(TestStringKey).first() shouldBe null
    }

    @Test
    fun `different keys do not interfere`() = runTest {
        val ctrl = NavigationController()

        ctrl.setResult(TestStringKey, "hello")
        ctrl.setResult(TestIntKey, 42)

        ctrl.resultFlow(TestStringKey).filterNotNull().first() shouldBe "hello"
        ctrl.resultFlow(TestIntKey).filterNotNull().first() shouldBe 42

        ctrl.consumeResult(TestStringKey)
        ctrl.resultFlow(TestStringKey).first() shouldBe null
        ctrl.resultFlow(TestIntKey).filterNotNull().first() shouldBe 42
    }

    @Test
    fun `late subscriber sees pre-written value`() = runTest {
        val ctrl = NavigationController()

        ctrl.setResult(TestStringKey, "pre-written")

        val collected = ctrl.resultFlow(TestStringKey).filterNotNull().first()
        collected shouldBe "pre-written"
    }

    @Test
    fun `consumeResults flow clears the slot after emission`() = runTest {
        val ctrl = NavigationController()

        ctrl.setResult(TestStringKey, "first")
        ctrl.consumeResults(TestStringKey).first() shouldBe "first"

        // Slot is cleared after the consumeResults collector processes the emission.
        ctrl.resultFlow(TestStringKey).first() shouldBe null
    }
}
