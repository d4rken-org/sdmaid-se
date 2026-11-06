package eu.darken.sdmse.stats.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.stats.core.db.ReportEntity
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.mockDataStoreValue
import java.io.File
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class StatsDbBackupContributorTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var appScope: CoroutineScope
    private lateinit var database: ReportsDatabase
    private lateinit var contributor: StatsDbBackupContributor

    private val statsSettings: StatsSettings = mockk {
        every { retentionReports } returns mockDataStoreValue(Duration.ofDays(30))
        every { retentionPaths } returns mockDataStoreValue(Duration.ofDays(7))
        every { retentionSnapshots } returns mockDataStoreValue(Duration.ofDays(90))
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        database = ReportsDatabase(
            appScope = appScope,
            context = context,
            statsSettings = statsSettings,
            aPathTypeConverter = APathTypeConverter(Json),
            dispatcherProvider = TestDispatcherProvider(),
        )
        runBlocking { appScope.coroutineContext[Job]!!.children.toList().joinAll() }
        contributor = StatsDbBackupContributor(context, database)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        database.roomDb.close()
        context.deleteDatabase("reports")
    }

    private fun report(endAt: Instant) = ReportEntity(
        startAt = endAt.minusSeconds(1),
        endAt = endAt,
        tool = SDMTool.Type.CORPSEFINDER,
        status = Report.Status.SUCCESS,
        primaryMessage = null,
        secondaryMessage = null,
        errorMessage = null,
        affectedCount = 1,
        affectedSpace = 1L,
        extra = null,
    )

    private fun withBackupOf(vararg reports: ReportEntity, block: suspend (File) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
        reports.forEach { database.addReport(it) }
        val backup = File.createTempFile("stats-", ".db")
        try {
            contributor.exportTo(backup)
            block(backup)
        } finally {
            backup.delete()
        }
    }

    @Test
    fun `Room can start tracking another table after a restore`() {
        val now = Instant.now()
        withBackupOf(report(now.minusSeconds(10))) { backup ->
            database.roomDb.openHelper.writableDatabase.isWriteAheadLoggingEnabled shouldBe true

            contributor.restoreFrom(backup, RestoreMode.REPLACE)

            withTimeout(5_000) { database.snapshotsCount.first() } shouldBe 0
            database.roomDb.openHelper.writableDatabase.isWriteAheadLoggingEnabled shouldBe true
        }
    }

    @Test
    fun `flows collecting during a restore see the restored rows`() {
        val now = Instant.now()
        withBackupOf(report(now.minusSeconds(20)), report(now.minusSeconds(10))) { backup ->
            database.addReport(report(now))
            val observed = MutableStateFlow(-1)
            coroutineScope {
                val observer = launch { database.reportCount.collect { observed.value = it } }
                withTimeout(5_000) { observed.first { it == 3 } }

                contributor.restoreFrom(backup, RestoreMode.REPLACE)

                withTimeout(5_000) { observed.first { it == 2 } }
                observer.cancel()
            }
        }
    }
}
