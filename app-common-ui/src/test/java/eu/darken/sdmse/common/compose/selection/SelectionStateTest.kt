package eu.darken.sdmse.common.compose.selection

import androidx.compose.runtime.saveable.SaverScope
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SelectionStateTest : BaseTest() {

    @Test
    fun `toggle adds then removes`() {
        val state = SelectionState<Int>()
        state.selected shouldBe emptySet()
        state.isActive shouldBe false
        state.count shouldBe 0

        state.toggle(1)
        state.selected shouldBe setOf(1)
        state.isActive shouldBe true
        state.count shouldBe 1
        state.contains(1) shouldBe true

        state.toggle(1)
        state.selected shouldBe emptySet()
        state.isActive shouldBe false
        state.contains(1) shouldBe false
    }

    @Test
    fun `select is idempotent and deselect removes`() {
        val state = SelectionState<Int>()
        state.select(2)
        state.select(2)
        state.selected shouldBe setOf(2)
        state.deselect(2)
        state.selected shouldBe emptySet()
    }

    @Test
    fun `setSelection replaces the whole selection`() {
        val state = SelectionState(setOf(1, 2))
        state.setSelection(setOf(3, 4))
        state.selected shouldBe setOf(3, 4)
    }

    @Test
    fun `clear empties the selection`() {
        val state = SelectionState(setOf(1, 2))
        state.clear()
        state.selected shouldBe emptySet()
        state.count shouldBe 0
    }

    @Test
    fun `retainAll drops ids that are no longer present`() {
        val state = SelectionState(setOf(1, 2, 3))
        state.retainAll(setOf(2, 3, 4))
        state.selected shouldBe setOf(2, 3)
    }

    @Test
    fun `constructor defensively copies the initial set`() {
        val source = mutableSetOf(1, 2)
        val state = SelectionState(source)
        source.add(3)
        state.selected shouldBe setOf(1, 2)
    }

    @Test
    fun `setSelection defensively copies its argument`() {
        val state = SelectionState<Int>()
        val source = mutableSetOf(1, 2)
        state.setSelection(source)
        source.add(3)
        state.selected shouldBe setOf(1, 2)
    }

    @Test
    fun `saver round-trips the current selection`() {
        val saver = SelectionState.saver<Int>()
        val original = SelectionState(setOf(5, 6, 7))

        val scope = SaverScope { true }
        val saved = with(saver) { scope.save(original) }
        val restored = saver.restore(saved!!)

        restored!!.selected shouldBe setOf(5, 6, 7)
    }
}
