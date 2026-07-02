package eu.darken.sdmse.common.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NavigationControllerGoToTest : BaseTest() {

    private enum class Dest : NavigationDestination { HOME, A, B, C, OTHER }

    private fun backStackOf(vararg keys: NavKey): NavBackStack<NavKey> = NavBackStack(*keys)

    private fun controllerWith(vararg keys: NavKey): Pair<NavigationController, NavBackStack<NavKey>> {
        val stack = backStackOf(*keys)
        val ctrl = NavigationController().apply { setup(stack) }
        return ctrl to stack
    }

    @Test
    fun `goTo without popUpTo just pushes`() {
        val (ctrl, stack) = controllerWith(Dest.HOME, Dest.A)

        ctrl.goTo(Dest.B)

        stack.shouldContainExactly(Dest.HOME, Dest.A, Dest.B)
    }

    @Test
    fun `popUpTo present non-inclusive truncates back to target then pushes`() {
        val (ctrl, stack) = controllerWith(Dest.HOME, Dest.A, Dest.B)

        ctrl.goTo(Dest.C, popUpTo = Dest.A, inclusive = false)

        // A is kept (non-inclusive), B popped, C pushed.
        stack.shouldContainExactly(Dest.HOME, Dest.A, Dest.C)
    }

    @Test
    fun `popUpTo present inclusive removes the target too then pushes`() {
        val (ctrl, stack) = controllerWith(Dest.HOME, Dest.A, Dest.B)

        ctrl.goTo(Dest.C, popUpTo = Dest.A, inclusive = true)

        // A is removed (inclusive), B popped, C pushed.
        stack.shouldContainExactly(Dest.HOME, Dest.C)
    }

    @Test
    fun `popUpTo to the root non-inclusive keeps only root and destination`() {
        val (ctrl, stack) = controllerWith(Dest.HOME, Dest.A, Dest.B)

        ctrl.goTo(Dest.C, popUpTo = Dest.HOME, inclusive = false)

        stack.shouldContainExactly(Dest.HOME, Dest.C)
    }

    @Test
    fun `popUpTo absent degrades to a plain push and does NOT empty the stack`() {
        val (ctrl, stack) = controllerWith(Dest.HOME, Dest.A, Dest.B)

        // OTHER is not on the stack — the old drain loop would pop HOME, A and B, leaving
        // a single-entry stack rooted at C. The guard must degrade this to a plain push.
        ctrl.goTo(Dest.C, popUpTo = Dest.OTHER, inclusive = false)

        stack.shouldContainExactly(Dest.HOME, Dest.A, Dest.B, Dest.C)
    }

    @Test
    fun `popUpTo absent with inclusive also degrades to a plain push`() {
        val (ctrl, stack) = controllerWith(Dest.HOME, Dest.A)

        ctrl.goTo(Dest.B, popUpTo = Dest.OTHER, inclusive = true)

        stack.shouldContainExactly(Dest.HOME, Dest.A, Dest.B)
    }

    @Test
    fun `goTo before setup is queued and drained on setup`() {
        val ctrl = NavigationController()

        // No setup() yet — the call must be queued, not crash.
        ctrl.goTo(Dest.A)

        val stack = backStackOf(Dest.HOME)
        ctrl.setup(stack)

        stack.shouldContainExactly(Dest.HOME, Dest.A)
    }

    @Test
    fun `queued goTo with popUpTo applies its pop semantics when drained`() {
        val ctrl = NavigationController()

        ctrl.goTo(Dest.C, popUpTo = Dest.A, inclusive = true)

        val stack = backStackOf(Dest.HOME, Dest.A, Dest.B)
        ctrl.setup(stack)

        // A removed (inclusive), B popped, C pushed during drain.
        stack.shouldContainExactly(Dest.HOME, Dest.C)
    }

    @Test
    fun `queued goTo with absent popUpTo degrades to push when drained`() {
        val ctrl = NavigationController()

        ctrl.goTo(Dest.C, popUpTo = Dest.OTHER, inclusive = true)

        val stack = backStackOf(Dest.HOME, Dest.A, Dest.B)
        ctrl.setup(stack)

        stack.shouldContainExactly(Dest.HOME, Dest.A, Dest.B, Dest.C)
    }

    @Test
    fun `up does not remove the last element`() {
        val (ctrl, stack) = controllerWith(Dest.HOME)

        ctrl.up() shouldBe false
        stack.shouldContainExactly(Dest.HOME)
    }

    @Test
    fun `up removes the top entry when more than one remains`() {
        val (ctrl, stack) = controllerWith(Dest.HOME, Dest.A)

        ctrl.up() shouldBe true
        stack.shouldContainExactly(Dest.HOME)
    }

    @Test
    fun `up from a sole non-home entry synthesizes the home parent`() {
        // Deep links seed a rootless stack; "up" must land on home instead of dead-ending.
        val stack = backStackOf(Dest.A)
        val ctrl = NavigationController().apply { setup(stack, homeRoute = Dest.HOME) }

        ctrl.up() shouldBe true
        stack.shouldContainExactly(Dest.HOME)
    }

    @Test
    fun `up from a sole entry that IS home stays a no-op`() {
        val stack = backStackOf(Dest.HOME)
        val ctrl = NavigationController().apply { setup(stack, homeRoute = Dest.HOME) }

        ctrl.up() shouldBe false
        stack.shouldContainExactly(Dest.HOME)
    }

    @Test
    fun `up from a sole entry without a home route keeps the legacy no-op`() {
        val (ctrl, stack) = controllerWith(Dest.A)

        ctrl.up() shouldBe false
        stack.shouldContainExactly(Dest.A)
    }

    @Test
    fun `up with home route set pops normally when more than one entry remains`() {
        val stack = backStackOf(Dest.A, Dest.B)
        val ctrl = NavigationController().apply { setup(stack, homeRoute = Dest.HOME) }

        ctrl.up() shouldBe true
        stack.shouldContainExactly(Dest.A)
    }

    @Test
    fun `up never seeds an empty stack even with a home route`() {
        val stack = backStackOf()
        val ctrl = NavigationController().apply { setup(stack, homeRoute = Dest.HOME) }

        ctrl.up() shouldBe false
        stack.shouldContainExactly()
    }

    @Test
    fun `setup without home clears a previously set home route`() {
        // Singleton controller: a home route from one activity setup must not leak into the next.
        val ctrl = NavigationController()
        ctrl.setup(backStackOf(Dest.A), homeRoute = Dest.HOME)

        val stack = backStackOf(Dest.A)
        ctrl.setup(stack)

        ctrl.up() shouldBe false
        stack.shouldContainExactly(Dest.A)
    }

    @Test
    fun `plain entry resets a rootless deep-link stack to home`() {
        val stack = backStackOf(Dest.A)
        val ctrl = NavigationController().apply { setup(stack, homeRoute = Dest.HOME) }

        ctrl.resetToHomeOnPlainEntry()

        stack.shouldContainExactly(Dest.HOME)
    }

    @Test
    fun `plain entry resets a DEEP rootless stack to home`() {
        val stack = backStackOf(Dest.A, Dest.B, Dest.C)
        val ctrl = NavigationController().apply { setup(stack, homeRoute = Dest.HOME) }

        ctrl.resetToHomeOnPlainEntry()

        stack.shouldContainExactly(Dest.HOME)
    }

    @Test
    fun `plain entry leaves a home-rooted stack untouched`() {
        // Normal sessions keep resume-where-you-left-off semantics.
        val stack = backStackOf(Dest.HOME, Dest.A, Dest.B)
        val ctrl = NavigationController().apply { setup(stack, homeRoute = Dest.HOME) }

        ctrl.resetToHomeOnPlainEntry()

        stack.shouldContainExactly(Dest.HOME, Dest.A, Dest.B)
    }

    @Test
    fun `plain entry without a home route is a no-op`() {
        val (ctrl, stack) = controllerWith(Dest.A, Dest.B)

        ctrl.resetToHomeOnPlainEntry()

        stack.shouldContainExactly(Dest.A, Dest.B)
    }

    @Test
    fun `plain entry before setup is a no-op`() {
        // Doesn't crash on the uninitialized singleton.
        NavigationController().resetToHomeOnPlainEntry()
    }

    @Test
    fun `setup with home route still drains queued actions`() {
        val ctrl = NavigationController()
        ctrl.goTo(Dest.B)

        val stack = backStackOf(Dest.A)
        ctrl.setup(stack, homeRoute = Dest.HOME)

        stack.shouldContainExactly(Dest.A, Dest.B)
    }
}
