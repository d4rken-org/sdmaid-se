package eu.darken.sdmse.stats.core

import android.content.Context
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class StatsRepoTest : BaseTest() {

    @MockK lateinit var context: Context
    @MockK lateinit var reportsDatabase: ReportsDatabase
    @MockK lateinit var statsSettings: StatsSettings
    @MockK lateinit var spaceTracker: SpaceTracker

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { reportsDatabase.reports } returns flowOf(emptyList())
        every { reportsDatabase.reportCount } returns flowOf(0)
        every { reportsDatabase.snapshotsCount } returns flowOf(0)
        every { reportsDatabase.databaseSize } returns MutableStateFlow(0L)

        every { statsSettings.totalSpaceFreed.flow } returns flowOf(0L)
        every { statsSettings.totalItemsProcessed.flow } returns flowOf(0L)

        coEvery { spaceTracker.recordSnapshot(force = any<Boolean>()) } returns Unit
    }

    @Test
    fun `report does not record snapshot directly for non-reportable tasks`() = runTest {
        val tool = mockk<SDMTool>(relaxed = true).also {
            every { it.type } returns SDMTool.Type.ANALYZER
        }
        val task = mockk<SDMTool.Task>(relaxed = true).also {
            every { it.type } returns SDMTool.Type.ANALYZER
        }
        val managedTask = TaskManager.ManagedTask(
            id = "task-id",
            task = task,
            tool = tool,
            completedAt = Instant.now(),
        )

        val repo = StatsRepo(
            appScope = backgroundScope,
            context = context,
            reportsDatabase = reportsDatabase,
            statsSettings = statsSettings,
            spaceTracker = spaceTracker,
        )

        repo.report(managedTask)

        coVerify(exactly = 0) { spaceTracker.recordSnapshot(force = any<Boolean>()) }
    }

    @Test
    fun `recordSnapshot delegates to spaceTracker`() = runTest {
        val repo = StatsRepo(
            appScope = backgroundScope,
            context = context,
            reportsDatabase = reportsDatabase,
            statsSettings = statsSettings,
            spaceTracker = spaceTracker,
        )

        repo.recordSnapshot(force = true)

        coVerify(exactly = 1) { spaceTracker.recordSnapshot(force = true) }
    }
}
