package eu.darken.sdmse.stats.core.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.stats.core.StatsSettings
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
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
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.mockDataStoreValue
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ReportsDatabaseRetentionTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var appScope: CoroutineScope
    private lateinit var database: ReportsDatabase

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
        // The retention flows emit once and complete, so the init-time prunes finish here.
        runBlocking { appScope.coroutineContext[Job]!!.children.toList().joinAll() }
    }

    @After
    fun tearDown() {
        appScope.cancel()
        database.roomDb.close()
        context.deleteDatabase("reports")
    }

    private fun snapshot(recordedAt: Instant) = SpaceSnapshotEntity(
        storageId = "storage-a",
        recordedAt = recordedAt,
        spaceFree = 1L,
        spaceCapacity = 2L,
    )

    @Test
    fun `applyRetention prunes snapshots older than the snapshot retention`() = runBlocking<Unit> {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val expired = now - Duration.ofDays(100)
        val recent = now - Duration.ofDays(10)
        database.spaceSnapshotDao.insert(snapshot(expired))
        database.spaceSnapshotDao.insert(snapshot(recent))

        database.applyRetention()

        database.spaceSnapshotDao.getAll(Instant.EPOCH).first().map { it.recordedAt } shouldBe listOf(recent)
    }
}
