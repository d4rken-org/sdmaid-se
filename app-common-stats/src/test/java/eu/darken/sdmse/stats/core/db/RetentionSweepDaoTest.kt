package eu.darken.sdmse.stats.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.AffectedPkg
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RetentionSweepDaoTest : BaseTest() {

    private lateinit var db: ReportsRoomDb
    private lateinit var reports: ReportsDao
    private lateinit var paths: AffectedPathsDao
    private lateinit var pkgs: AffectedPkgsDao

    private val now = Instant.parse("2026-06-01T10:00:00Z")

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReportsRoomDb::class.java)
            .addTypeConverter(APathTypeConverter(Json))
            .allowMainThreadQueries()
            .build()
        reports = db.reports()
        paths = db.paths()
        pkgs = db.pkgs()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun pathRow(reportId: ReportId) = AffectedPathEntity(
        reportId = reportId,
        action = AffectedPath.Action.DELETED,
        path = LocalPath.build("/data/media/0/$reportId"),
    )

    private fun pkgRow(reportId: ReportId) = AffectedPkgEntity(
        reportId = reportId,
        action = AffectedPkg.Action.DELETED,
        pkgId = "eu.thedarken.sdm".toPkgId(),
    )

    /** A report plus one path and one pkg child. */
    private fun seed(endAt: Instant): ReportId {
        val entity = ReportEntity(
            startAt = endAt.minusSeconds(1),
            endAt = endAt,
            tool = SDMTool.Type.APPCLEANER,
            status = Report.Status.SUCCESS,
            primaryMessage = null,
            secondaryMessage = null,
            errorMessage = null,
            affectedCount = 1,
            affectedSpace = 1L,
            extra = null,
        )
        reports.insert(entity)
        paths.insert(listOf(pathRow(entity.reportId)))
        pkgs.insert(listOf(pkgRow(entity.reportId)))
        return entity.reportId
    }

    /** Mirrors the sweep order of ReportsDatabase.pruneReports: children first, then parents, then orphans. */
    private fun sweep(cutOff: Instant): Int {
        val deletedPaths = paths.deleteForReportsOlderThan(cutOff)
        val deletedPkgs = pkgs.deleteForReportsOlderThan(cutOff)
        val deletedReports = reports.deleteOlderThan(cutOff)
        return deletedPaths + deletedPkgs + deletedReports + paths.deleteOrphans() + pkgs.deleteOrphans()
    }

    @Test
    fun `the cutoff sweep takes the expired report with its children and leaves the recent one`() =
        runBlocking<Unit> {
            val expired = seed(now.minusSeconds(600))
            val recent = seed(now.plusSeconds(600))

            sweep(now) shouldBe 3

            reports.getById(expired).shouldBeNull()
            paths.getById(expired).shouldBeEmpty()
            pkgs.getById(expired).shouldBeEmpty()

            reports.getById(recent).shouldNotBeNull()
            paths.getById(recent).size shouldBe 1
            pkgs.getById(recent).size shouldBe 1
        }

    @Test
    fun `orphan deletes reclaim children whose report is already gone`() = runBlocking<Unit> {
        val live = seed(now.plusSeconds(600))
        val ghost: ReportId = UUID.randomUUID()
        paths.insert(listOf(pathRow(ghost)))
        pkgs.insert(listOf(pkgRow(ghost)))

        paths.deleteOrphans() shouldBe 1
        pkgs.deleteOrphans() shouldBe 1

        paths.getById(ghost).shouldBeEmpty()
        pkgs.getById(ghost).shouldBeEmpty()
        paths.getById(live).size shouldBe 1
        pkgs.getById(live).size shouldBe 1
    }

    @Test
    fun `a history far beyond the SQLite bind parameter limit sweeps in one go`() = runBlocking<Unit> {
        // An ID-keyed delete would bind one parameter per report and trip SQLite's 999 limit here.
        val count = 1100
        val seeded = mutableListOf<ReportId>()
        db.runInTransaction(Runnable { repeat(count) { seeded += seed(now.minusSeconds(600)) } })

        sweep(now) shouldBe count * 3

        seeded.forEach { id ->
            reports.getById(id).shouldBeNull()
            paths.getById(id).shouldBeEmpty()
            pkgs.getById(id).shouldBeEmpty()
        }
    }
}
