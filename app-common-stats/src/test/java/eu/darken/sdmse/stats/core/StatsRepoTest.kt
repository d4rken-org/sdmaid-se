package eu.darken.sdmse.stats.core

import android.content.Context
import android.os.Parcel
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue
import java.io.IOException
import java.time.Duration
import java.time.Instant

class StatsRepoTest : BaseTest() {

    // StatsRepo.state does a stateIn() on construction; that sharing coroutine must not live in the
    // test scope, which would then never see all its children complete.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopRepoScope() {
        repoScope.coroutineContext[Job]?.cancel()
    }

    private val reportsDatabase: ReportsDatabase = mockk(relaxed = true)
    private val spaceFreed: DataStoreValue<Long> = mockk(relaxed = true)
    private val itemsProcessed: DataStoreValue<Long> = mockk(relaxed = true)

    // Read via answers, so a test can change them before it calls reportOf().
    private var reportsRetention: Duration = Duration.ofDays(30)
    private var pathsRetention: Duration = Duration.ofDays(7)

    private val statsSettings: StatsSettings = mockk(relaxed = true) {
        every { totalSpaceFreed } returns spaceFreed
        every { totalItemsProcessed } returns itemsProcessed
        every { retentionReports } answers { mockDataStoreValue(reportsRetention) }
        every { retentionPaths } answers { mockDataStoreValue(pathsRetention) }
    }
    private val context: Context = mockk(relaxed = true)

    private class TestTask : SDMTool.Task, Reportable {
        override val type: SDMTool.Type = SDMTool.Type.APPCLEANER
        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    }

    private open class PlainResult : SDMTool.Task.Result {
        override val type: SDMTool.Type = SDMTool.Type.APPCLEANER
        override val primaryInfo: CaString = "46 expendable items deleted".toCaString()
        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    }

    /** [ReportDetails.AffectedCount], not AffectedPaths: the affected-path table is not what these assert. */
    private class DetailedResult(
        override val affectedSpace: Long,
        override val affectedCount: Int,
        override val status: Report.Status = Report.Status.SUCCESS,
    ) : PlainResult(), ReportDetails.AffectedSpace, ReportDetails.AffectedCount

    /**
     * Carries both detail kinds, so an `exactly = 0` on addPaths/addPkgs can only pass because
     * retention gated it — a result missing either interface never reaches those calls at all.
     */
    private class PathsAndPkgsResult : PlainResult(), ReportDetails.AffectedPaths, ReportDetails.AffectedPkgs {
        override val affectedPaths: Set<APath> = setOf(LocalPath.build("/data/cache/thing"))
        override val affectedPkgs: Map<Pkg.Id, AffectedPkg.Action> = mapOf(
            "eu.thedarken.sdm".toPkgId() to AffectedPkg.Action.DELETED,
        )
        override val affectedCount: Int = affectedPaths.size + affectedPkgs.size
    }

    private fun managedTask(
        result: SDMTool.Task.Result?,
        error: Throwable?,
    ) = TaskSubmitter.ManagedTask(
        id = "task-1",
        toolType = SDMTool.Type.APPCLEANER,
        task = TestTask(),
        startedAt = Instant.EPOCH,
        completedAt = Instant.EPOCH.plusSeconds(60),
        result = result,
        error = error,
    )

    private fun newRepo(): StatsRepo {
        // A relaxed mock would swallow the transaction block and nothing would ever be written.
        coEvery { reportsDatabase.withTransaction<Any?>(any()) } coAnswers {
            firstArg<suspend () -> Any?>().invoke()
        }
        return StatsRepo(
            appScope = repoScope,
            context = context,
            reportsDatabase = reportsDatabase,
            statsSettings = statsSettings,
            spaceTracker = mockk(relaxed = true),
        )
    }

    /** Reports [task] and returns what was written to the database. */
    private suspend fun reportOf(task: TaskSubmitter.ManagedTask): Report {
        val repo = newRepo()
        val captured = slot<Report>()
        coEvery { reportsDatabase.addReport(capture(captured)) } returns Unit
        repo.report(task)
        return captured.captured
    }

    @Test
    fun `a failure that still produced details is a partial success`() = runTest2 {
        val report = reportOf(
            managedTask(DetailedResult(affectedSpace = 512L, affectedCount = 3), IOException("screen went off")),
        )

        report.status shouldBe Report.Status.PARTIAL_SUCCESS
        // Both halves are kept: what was freed and what went wrong.
        report.affectedSpace shouldBe 512L
        report.affectedCount shouldBe 3
        report.primaryMessage shouldBe "46 expendable items deleted"
        report.errorMessage!! shouldContain "screen went off"

        // The lifetime counters must see the salvaged run too.
        val spaceUpdate = slot<(Long) -> Long?>()
        val itemsUpdate = slot<(Long) -> Long?>()
        coVerify { spaceFreed.update(capture(spaceUpdate)) }
        coVerify { itemsProcessed.update(capture(itemsUpdate)) }
        spaceUpdate.captured.invoke(1_000L) shouldBe 1_512L
        itemsUpdate.captured.invoke(10L) shouldBe 13L
    }

    @Test
    fun `a failure with nothing to show is a failure`() = runTest2 {
        val report = reportOf(managedTask(null, IOException("screen went off")))

        report.status shouldBe Report.Status.FAILURE
        report.affectedSpace shouldBe null
        report.affectedCount shouldBe null
    }

    @Test
    fun `a successful run keeps the status its details report`() = runTest2 {
        val report = reportOf(
            managedTask(
                DetailedResult(affectedSpace = 1L, affectedCount = 1, status = Report.Status.PARTIAL_SUCCESS),
                error = null,
            ),
        )

        report.status shouldBe Report.Status.PARTIAL_SUCCESS
    }

    @Test
    fun `a result without details is a plain success`() = runTest2 {
        val report = reportOf(managedTask(PlainResult(), error = null))

        report.status shouldBe Report.Status.SUCCESS
    }

    @Test
    fun `zero reports retention stores neither the report nor its details`() = runTest2 {
        reportsRetention = Duration.ZERO

        newRepo().report(managedTask(PathsAndPkgsResult(), error = null))

        coVerify(exactly = 0) { reportsDatabase.addReport(any()) }
        coVerify(exactly = 0) { reportsDatabase.addPaths(any()) }
        coVerify(exactly = 0) { reportsDatabase.addPkgs(any()) }

        // The lifetime odometer keeps running, it is not what retention governs.
        coVerify { itemsProcessed.update(any()) }
    }

    @Test
    fun `a negative reports retention is treated like zero`() = runTest2 {
        reportsRetention = Duration.ofDays(-1)

        newRepo().report(managedTask(PathsAndPkgsResult(), error = null))

        coVerify(exactly = 0) { reportsDatabase.addReport(any()) }
    }

    @Test
    fun `zero paths retention drops only the affected files`() = runTest2 {
        reportsRetention = Duration.ofDays(30)
        pathsRetention = Duration.ZERO

        newRepo().report(managedTask(PathsAndPkgsResult(), error = null))

        coVerify(exactly = 1) { reportsDatabase.addReport(any()) }
        coVerify(exactly = 0) { reportsDatabase.addPaths(any()) }
        coVerify(exactly = 1) { reportsDatabase.addPkgs(any()) }
    }

    @Test
    fun `positive retentions store the report and both detail kinds`() = runTest2 {
        reportsRetention = Duration.ofDays(30)
        pathsRetention = Duration.ofDays(7)

        newRepo().report(managedTask(PathsAndPkgsResult(), error = null))

        coVerify(exactly = 1) { reportsDatabase.addReport(any()) }
        coVerify(exactly = 1) { reportsDatabase.addPaths(any()) }
        coVerify(exactly = 1) { reportsDatabase.addPkgs(any()) }
    }

    @Test
    fun `a stored report sweeps expired history`() = runTest2 {
        newRepo().report(managedTask(PathsAndPkgsResult(), error = null))

        coVerify { reportsDatabase.applyRetention() }
    }
}
