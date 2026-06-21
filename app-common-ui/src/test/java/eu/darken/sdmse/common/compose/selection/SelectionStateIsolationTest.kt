package eu.darken.sdmse.common.compose.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Verifies the recomposition-isolation contract of [SelectionState] — the whole point of the holder.
 *
 * Each probe row bumps a per-id counter in a [SideEffect]; a [SideEffect] only runs for a row that
 * actually (re)composed, so counter deltas measure recompositions.
 */
class SelectionStateIsolationTest : BaseComposeRobolectricTest() {

    @Composable
    private fun ProbeRow(id: Int, holder: SelectionState<Int>, counters: MutableMap<Int, Int>) {
        val selected = holder.isSelected(id)
        SideEffect { counters[id] = (counters[id] ?: 0) + 1 }
        Text("row$id=$selected")
    }

    @Composable
    private fun ModeAwareRow(id: Int, holder: SelectionState<Int>, counters: MutableMap<Int, Int>) {
        val selected = holder.isSelected(id)
        val active = holder.isActive
        SideEffect { counters[id] = (counters[id] ?: 0) + 1 }
        Text("row$id=$selected active=$active")
    }

    @Test
    fun `steady-active toggle recomposes only the toggled row`() {
        val holder = SelectionState<Int>()
        val counters = mutableMapOf<Int, Int>()
        // Pre-select a row so selection is already active — isolate the steady-state behavior from the
        // empty<->non-empty transition.
        holder.select(0)

        composeRule.setContent {
            PreviewWrapper {
                Column {
                    (0..4).forEach { ProbeRow(it, holder, counters) }
                }
            }
        }
        composeRule.waitForIdle()
        val baseline = counters.toMap()

        composeRule.runOnIdle { holder.toggle(2) }
        composeRule.waitForIdle()

        (0..4).forEach { id ->
            val delta = (counters[id] ?: 0) - (baseline[id] ?: 0)
            assertEquals("row $id recompositions after toggling row 2", if (id == 2) 1 else 0, delta)
        }
    }

    @Test
    fun `entering selection mode recomposes all mode-aware rows`() {
        val holder = SelectionState<Int>()
        val counters = mutableMapOf<Int, Int>()

        composeRule.setContent {
            PreviewWrapper {
                Column {
                    (0..4).forEach { ModeAwareRow(it, holder, counters) }
                }
            }
        }
        composeRule.waitForIdle()
        val baseline = counters.toMap()

        // empty -> active flips isActive, which every mode-aware row reads: all recompose once (expected).
        composeRule.runOnIdle { holder.select(0) }
        composeRule.waitForIdle()

        (0..4).forEach { id ->
            val delta = (counters[id] ?: 0) - (baseline[id] ?: 0)
            assertEquals("row $id recompositions when entering selection mode", 1, delta)
        }
    }

    @Test
    fun `isActive notifies only on the empty to non-empty transition`() {
        val holder = SelectionState<Int>()
        var bannerCompositions = 0

        composeRule.setContent {
            PreviewWrapper {
                val active = holder.isActive
                SideEffect { bannerCompositions++ }
                Text("active=$active")
            }
        }
        composeRule.waitForIdle()
        val afterInitial = bannerCompositions

        composeRule.runOnIdle { holder.select(1) } // empty -> active: notifies
        composeRule.waitForIdle()
        assertEquals(1, bannerCompositions - afterInitial)
        val afterFirst = bannerCompositions

        composeRule.runOnIdle { holder.select(2) } // active -> active: no notify
        composeRule.waitForIdle()
        assertEquals(0, bannerCompositions - afterFirst)

        composeRule.runOnIdle { holder.clear() } // active -> empty: notifies
        composeRule.waitForIdle()
        assertEquals(1, bannerCompositions - afterFirst)
    }

    @Test
    fun `isSelected re-evaluates when the id argument changes`() {
        val holder = SelectionState(setOf(10))
        val idState = mutableStateOf(10)
        var lastSelected = false

        composeRule.setContent {
            PreviewWrapper {
                val id by idState
                val selected = holder.isSelected(id)
                SideEffect { lastSelected = selected }
                Text("sel=$selected")
            }
        }
        composeRule.waitForIdle()
        assertEquals(true, lastSelected)

        composeRule.runOnIdle { idState.value = 11 }
        composeRule.waitForIdle()
        assertEquals(false, lastSelected)

        composeRule.runOnIdle { idState.value = 10 }
        composeRule.waitForIdle()
        assertEquals(true, lastSelected)
    }
}
