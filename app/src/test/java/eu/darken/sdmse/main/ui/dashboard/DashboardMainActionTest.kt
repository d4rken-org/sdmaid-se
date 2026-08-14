package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Contract of [DashboardMainActionEngine.resolveMainAction]: which results arm the main button's DELETE
 * action (and thereby summon the hero card). A tool only counts when it is opted into one-click
 * (Deduplicator additionally requires Pro) — matching what the DELETE branch of mainAction will
 * actually free, so the button never offers a deletion that provably does nothing.
 */
class DashboardMainActionTest : BaseTest() {

    private val idle = TaskSubmitter.State()

    private fun corpse() = mockk<CorpseFinder.Data> {
        every { corpses } returns setOf(mockk())
    }

    private fun system() = mockk<SystemCleaner.Data> {
        every { filterContents } returns setOf(mockk())
    }

    private fun app(unclearable: Boolean = false) = mockk<AppCleaner.Data> {
        every { junks } returns setOf(mockk { every { isUnclearable } returns unclearable })
    }

    private fun dedupe() = mockk<Deduplicator.Data> {
        every { clusters } returns setOf(mockk<Duplicate.Cluster>())
    }

    private fun oneClick(
        corpse: Boolean = true,
        system: Boolean = true,
        app: Boolean = true,
        dedupe: Boolean = false,
    ) = OneClickOptionsState(
        corpseFinderEnabled = corpse,
        systemCleanerEnabled = system,
        appCleanerEnabled = app,
        deduplicatorEnabled = dedupe,
    )

    private fun resolve(
        taskState: TaskSubmitter.State = idle,
        corpse: CorpseFinder.Data? = null,
        system: SystemCleaner.Data? = null,
        app: AppCleaner.Data? = null,
        dedupe: Deduplicator.Data? = null,
        oneClick: OneClickOptionsState = oneClick(),
        isPro: Boolean = true,
        oneClickMode: Boolean = false,
    ) = DashboardMainActionEngine.resolveMainAction(
        taskState = taskState,
        corpse = corpse,
        system = system,
        app = app,
        dedupe = dedupe,
        oneClick = oneClick,
        isPro = isPro,
        oneClickMode = oneClickMode,
    )

    @Test
    fun `no data resolves to SCAN, or ONECLICK when one-click mode is on`() {
        resolve() shouldBe BottomBarState.Action.SCAN
        resolve(oneClickMode = true) shouldBe BottomBarState.Action.ONECLICK
    }

    @Test
    fun `default tool data arms DELETE`() {
        resolve(corpse = corpse()) shouldBe BottomBarState.Action.DELETE
    }

    @Test
    fun `data does not arm DELETE when its one-click toggle is off`() {
        // mainAction's DELETE branch returns early for a tool that is opted out, so arming here
        // would offer a button that submits no task at all.
        resolve(
            corpse = corpse(),
            oneClick = oneClick(corpse = false),
        ) shouldBe BottomBarState.Action.SCAN
        resolve(
            system = system(),
            oneClick = oneClick(system = false),
        ) shouldBe BottomBarState.Action.SCAN
        resolve(
            app = app(),
            oneClick = oneClick(app = false),
        ) shouldBe BottomBarState.Action.SCAN
    }

    @Test
    fun `an opted-in tool still arms DELETE when a different tool is opted out`() {
        resolve(
            corpse = corpse(),
            app = app(),
            oneClick = oneClick(corpse = false, app = true),
        ) shouldBe BottomBarState.Action.DELETE
    }

    @Test
    fun `AppCleaner data arms DELETE without Pro so mainAction can upsell`() {
        resolve(
            app = app(),
            isPro = false,
        ) shouldBe BottomBarState.Action.DELETE
    }

    @Test
    fun `unclearable-only AppCleaner data does not arm DELETE`() {
        // Junks whose clearing permanently failed (no settings page, locked app) would make
        // DELETE report "0 deleted" forever — the button falls back to SCAN/ONECLICK instead.
        resolve(app = app(unclearable = true)) shouldBe BottomBarState.Action.SCAN
        resolve(
            app = app(unclearable = true),
            oneClickMode = true,
        ) shouldBe BottomBarState.Action.ONECLICK
    }

    @Test
    fun `dedupe-only data arms DELETE when opted into one-click and Pro`() {
        resolve(
            dedupe = dedupe(),
            oneClick = oneClick(dedupe = true),
            isPro = true,
        ) shouldBe BottomBarState.Action.DELETE
    }

    @Test
    fun `dedupe-only data does not arm DELETE when its one-click toggle is off`() {
        resolve(
            dedupe = dedupe(),
            oneClick = oneClick(dedupe = false),
            isPro = true,
        ) shouldBe BottomBarState.Action.SCAN
    }

    @Test
    fun `dedupe-only data does not arm DELETE for non-Pro users`() {
        // The DELETE branch of mainAction would skip Deduplicator without Pro, so offering the
        // action (and the hero) would not be action-truthful.
        resolve(
            dedupe = dedupe(),
            oneClick = oneClick(dedupe = true),
            isPro = false,
        ) shouldBe BottomBarState.Action.SCAN
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
        ) shouldBe BottomBarState.Action.WORKING_CANCELABLE

        val working = mockk<TaskSubmitter.State> {
            every { hasCancellable } returns false
            every { isIdle } returns false
        }
        resolve(
            taskState = working,
            corpse = corpse(),
        ) shouldBe BottomBarState.Action.WORKING
    }
}
