package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.main.ui.dashboard.DashboardMainActionEngine.AutoExpandDecision
import eu.darken.sdmse.main.ui.dashboard.DashboardMainActionEngine.BatchState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Contract of [DashboardMainActionEngine.resolveAutoExpand]: whether a hero snapshot arriving at the
 * settle collector expands the card, merely consumes the batch's one-shot arm, or is none of its
 * business. The arm is consumed by any settled snapshot of the armed batch — including one with
 * nothing to show, or the next card-triggered scan would inherit it and pop the hero open by itself.
 */
internal class DashboardAutoExpandDecisionTest : BaseTest() {

    private val armed = BatchState(id = 7L, pending = 0, autoExpandArmed = true)

    private fun decide(
        snapshotBatchId: Long = armed.id,
        isSettled: Boolean = true,
        hasSummary: Boolean = true,
        batch: BatchState = armed,
    ) = DashboardMainActionEngine.resolveAutoExpand(
        snapshotBatchId = snapshotBatchId,
        isSettled = isSettled,
        hasSummary = hasSummary,
        batch = batch,
    )

    @Test
    fun `an unsettled snapshot is ignored`() {
        decide(isSettled = false) shouldBe AutoExpandDecision.IGNORE
        decide(isSettled = false, hasSummary = false) shouldBe AutoExpandDecision.IGNORE
    }

    @Test
    fun `a settled snapshot without an arm is ignored`() {
        // Results the user didn't ask for with the one-tap button: a tool card's scan, an in-tool
        // screen, the scheduler. They render into the bar, they never open the hero.
        val disarmed = armed.copy(autoExpandArmed = false)

        decide(batch = disarmed) shouldBe AutoExpandDecision.IGNORE
        decide(batch = disarmed, hasSummary = false) shouldBe AutoExpandDecision.IGNORE
    }

    @Test
    fun `a settled snapshot from a superseded batch is ignored`() {
        // The ABA row: batch A's resolution can still arrive after batch B armed, and the two arms
        // are the same indistinguishable `true`. Without the id check, A consumes B's arm and B's
        // result — the one the user is waiting for — never expands.
        decide(snapshotBatchId = armed.id - 1) shouldBe AutoExpandDecision.IGNORE
        decide(snapshotBatchId = armed.id - 1, hasSummary = false) shouldBe AutoExpandDecision.IGNORE
        decide(snapshotBatchId = armed.id + 1) shouldBe AutoExpandDecision.IGNORE
    }

    @Test
    fun `a settled snapshot of the armed batch with nothing to show only disarms`() {
        decide(hasSummary = false) shouldBe AutoExpandDecision.DISARM
    }

    @Test
    fun `a settled snapshot of the armed batch with a hero expands and disarms`() {
        decide() shouldBe AutoExpandDecision.EXPAND_AND_DISARM
    }
}
