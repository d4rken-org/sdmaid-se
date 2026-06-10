package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Contract of [DashboardViewModel.resolveMainAction]: which results arm the main button's DELETE
 * action (and thereby summon the hero card). Deduplicator only counts when it is opted into
 * one-click and the user is Pro — matching what the DELETE branch of mainAction will actually free.
 */
class DashboardMainActionTest : BaseTest() {

    private val idle = TaskSubmitter.State()

    private fun corpse() = mockk<CorpseFinder.Data> {
        every { corpses } returns setOf(mockk())
    }

    private fun dedupe() = mockk<Deduplicator.Data> {
        every { clusters } returns setOf(mockk<Duplicate.Cluster>())
    }

    private fun oneClick(dedupe: Boolean = false) = DashboardViewModel.OneClickOptionsState(
        deduplicatorEnabled = dedupe,
    )

    private fun resolve(
        taskState: TaskSubmitter.State = idle,
        corpse: CorpseFinder.Data? = null,
        dedupe: Deduplicator.Data? = null,
        oneClick: DashboardViewModel.OneClickOptionsState = oneClick(),
        isPro: Boolean = true,
        oneClickMode: Boolean = false,
    ) = DashboardViewModel.resolveMainAction(
        taskState = taskState,
        corpse = corpse,
        system = null,
        app = null,
        dedupe = dedupe,
        oneClick = oneClick,
        isPro = isPro,
        oneClickMode = oneClickMode,
    )

    @Test
    fun `no data resolves to SCAN, or ONECLICK when one-click mode is on`() {
        resolve() shouldBe DashboardViewModel.BottomBarState.Action.SCAN
        resolve(oneClickMode = true) shouldBe DashboardViewModel.BottomBarState.Action.ONECLICK
    }

    @Test
    fun `default tool data arms DELETE`() {
        resolve(corpse = corpse()) shouldBe DashboardViewModel.BottomBarState.Action.DELETE
    }

    @Test
    fun `dedupe-only data arms DELETE when opted into one-click and Pro`() {
        resolve(
            dedupe = dedupe(),
            oneClick = oneClick(dedupe = true),
            isPro = true,
        ) shouldBe DashboardViewModel.BottomBarState.Action.DELETE
    }

    @Test
    fun `dedupe-only data does not arm DELETE when its one-click toggle is off`() {
        resolve(
            dedupe = dedupe(),
            oneClick = oneClick(dedupe = false),
            isPro = true,
        ) shouldBe DashboardViewModel.BottomBarState.Action.SCAN
    }

    @Test
    fun `dedupe-only data does not arm DELETE for non-Pro users`() {
        // The DELETE branch of mainAction would skip Deduplicator without Pro, so offering the
        // action (and the hero) would not be action-truthful.
        resolve(
            dedupe = dedupe(),
            oneClick = oneClick(dedupe = true),
            isPro = false,
        ) shouldBe DashboardViewModel.BottomBarState.Action.SCAN
    }

    @Test
    fun `running tasks take precedence over results`() {
        val cancellable = mockk<TaskSubmitter.State> {
            every { hasCancellable } returns true
            every { isIdle } returns false
        }
        resolve(
            taskState = cancellable,
            corpse = corpse(),
        ) shouldBe DashboardViewModel.BottomBarState.Action.WORKING_CANCELABLE

        val working = mockk<TaskSubmitter.State> {
            every { hasCancellable } returns false
            every { isIdle } returns false
        }
        resolve(
            taskState = working,
            corpse = corpse(),
        ) shouldBe DashboardViewModel.BottomBarState.Action.WORKING
    }
}
