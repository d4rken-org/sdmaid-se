package eu.darken.sdmse.stats.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.Report
import kotlinx.serialization.json.Json
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ReportsDaoTest : BaseTest() {

    private lateinit var db: ReportsRoomDb
    private lateinit var dao: ReportsDao

    private val base = Instant.parse("2026-06-01T10:00:00Z")

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReportsRoomDb::class.java)
            // Required at build time for the affected-paths column; never invoked (no path rows here).
            .addTypeConverter(APathTypeConverter(Json))
            .allowMainThreadQueries()
            .build()
        dao = db.reports()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun report(
        tool: SDMTool.Type,
        endAt: Instant,
        status: Report.Status = Report.Status.SUCCESS,
    ) = ReportEntity(
        startAt = endAt.minusSeconds(1),
        endAt = endAt,
        tool = tool,
        status = status,
        primaryMessage = null,
        secondaryMessage = null,
        errorMessage = null,
        affectedCount = 1,
        affectedSpace = 1L,
        extra = null,
    )

    @Test
    fun `returns the first matching report after since, not a later same-tool one`() = runBlocking<Unit> {
        // A prior deletion (before the batch) + the user's deletion + a later background report.
        dao.insert(report(SDMTool.Type.CORPSEFINDER, base.minusSeconds(60))) // stale, before since
        val mine = report(SDMTool.Type.CORPSEFINDER, base.plusSeconds(2))
        dao.insert(mine)
        dao.insert(report(SDMTool.Type.CORPSEFINDER, base.plusSeconds(30))) // later background report

        val result = dao.getReportForToolSince(SDMTool.Type.CORPSEFINDER, base)

        result!!.endAt shouldBe mine.endAt
    }

    @Test
    fun `returns null when nothing is at or after since`() = runBlocking<Unit> {
        dao.insert(report(SDMTool.Type.SYSTEMCLEANER, base.minusSeconds(5)))

        dao.getReportForToolSince(SDMTool.Type.SYSTEMCLEANER, base).shouldBeNull()
    }

    @Test
    fun `ignores other tools`() = runBlocking<Unit> {
        dao.insert(report(SDMTool.Type.APPCLEANER, base.plusSeconds(1)))

        dao.getReportForToolSince(SDMTool.Type.CORPSEFINDER, base).shouldBeNull()
    }

    @Test
    fun `does not filter by status - a failed deletion is still returned`() = runBlocking<Unit> {
        val failed = report(SDMTool.Type.CORPSEFINDER, base.plusSeconds(1), status = Report.Status.FAILURE)
        dao.insert(failed)

        val result = dao.getReportForToolSince(SDMTool.Type.CORPSEFINDER, base)

        result!!.status shouldBe Report.Status.FAILURE
    }

    @Test
    fun `tie-breaks equal end_at by insertion id`() = runBlocking<Unit> {
        val first = report(SDMTool.Type.DEDUPLICATOR, base.plusSeconds(5))
        val second = report(SDMTool.Type.DEDUPLICATOR, base.plusSeconds(5))
        dao.insert(first) // lower auto id
        dao.insert(second)

        // Both share end_at; ASC id picks the first inserted.
        dao.getReportForToolSince(SDMTool.Type.DEDUPLICATOR, base)!!.reportId shouldBe first.reportId
    }
}
