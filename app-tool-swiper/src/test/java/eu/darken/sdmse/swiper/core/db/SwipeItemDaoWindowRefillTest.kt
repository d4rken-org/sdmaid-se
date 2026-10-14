package eu.darken.sdmse.swiper.core.db

import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWrapper
import android.os.CancellationSignal
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.room.APathListTypeConverter
import eu.darken.sdmse.common.room.APathTypeConverter
import eu.darken.sdmse.common.serialization.SerializationIOModule
import eu.darken.sdmse.swiper.core.SwipeDecision
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestApplication::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SwipeItemDaoWindowRefillTest : BaseTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val json = SerializationIOModule().json()
    private val dbName = "swiper-window-refill-${UUID.randomUUID()}.db"
    private val sessionId = "window-refill-session"

    private val writerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val writer = AtomicReference<Future<*>?>()
    private val armed = AtomicBoolean(false)
    private val hookFires = AtomicInteger(0)
    private val writerStarted = CountDownLatch(1)
    private val deleteCompleted = CountDownLatch(1)
    private val writerStartedBeforeRefill = AtomicBoolean(false)
    private val deleteCompletedBeforeRefill = AtomicBoolean(false)
    private val refillStartRow = AtomicInteger(-1)
    private val refillRequiredRow = AtomicInteger(-1)

    private lateinit var db: SwiperRoomDb
    private lateinit var dao: SwipeItemDao
    private lateinit var seeded: List<SwipeItemEntity>

    @Before
    fun setup() = runBlocking<Unit> {
        db = Room.databaseBuilder(context, SwiperRoomDb::class.java, dbName)
            .addTypeConverter(APathTypeConverter(json))
            .addTypeConverter(APathListTypeConverter(json))
            .addTypeConverter(FileTypeFilterConverter(json))
            .openHelperFactory(RefillHookOpenHelperFactory())
            .build()
        dao = db.items()

        db.sessions().insert(
            SwipeSessionEntity(
                sessionId = sessionId,
                sourcePaths = listOf(LocalPath.build("/storage/emulated/0/DCIM")),
                totalItems = ITEM_COUNT,
            )
        )

        // E.g. 968 rows per window: the refill for row 968 re-runs the SELECT from row 646 (968 - 968 / 3).
        // 800 survivors leave that window with rows 646..799, so reading row 968 fails as window row 322.
        // Fewer than 647 fail with SQLiteBlobTooBigException instead, more than 968 return a mixed list.
        val padding = "p".repeat(PATH_PADDING)
        seeded = (0 until ITEM_COUNT).map { index ->
            val decision = when {
                index % SURVIVOR_CYCLE < SURVIVORS_PER_CYCLE / 2 -> SwipeDecision.UNDECIDED
                index % SURVIVOR_CYCLE < SURVIVORS_PER_CYCLE -> SwipeDecision.DELETE_FAILED
                index % 2 == 0 -> SwipeDecision.KEEP
                else -> SwipeDecision.DELETED
            }
            SwipeItemEntity(
                id = index + 1L,
                sessionId = sessionId,
                itemIndex = index,
                path = LocalPath.build("/storage/emulated/0/DCIM", padding, "item-$index.jpg"),
                decision = decision,
            )
        }
        dao.insertAll(seeded)
    }

    @After
    fun teardown() {
        try {
            writer.get()?.let { runCatching { it.get(WRITER_JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS) } }
        } finally {
            writerExecutor.shutdownNow()
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `flow read survives a bulk delete committed before the window refill`() = runBlocking<Unit> {
        verifyReadSurvivesConcurrentDelete { dao.getItemsForSession(sessionId).first() }
    }

    @Test
    fun `sync read survives a bulk delete committed before the window refill`() = runBlocking<Unit> {
        verifyReadSurvivesConcurrentDelete { dao.getItemsForSessionSync(sessionId) }
    }

    private suspend fun verifyReadSurvivesConcurrentDelete(read: suspend () -> List<SwipeItemEntity>) {
        armed.set(true)
        val result = read()
        armed.set(false)

        hookFires.get() shouldBe 1
        val survivors = seeded.filter {
            it.decision != SwipeDecision.DELETED && it.decision != SwipeDecision.KEEP
        }
        survivors.size shouldBeInRange (refillStartRow.get() + 1)..refillRequiredRow.get()
        log(TAG) {
            "Interleave: writerStartedBeforeRefill=${writerStartedBeforeRefill.get()}, " +
                "deleteCompletedBeforeRefill=${deleteCompletedBeforeRefill.get()}"
        }
        result shouldBe seeded

        checkNotNull(writer.get()).get(WRITER_JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        writerStarted.count shouldBe 0L
        deleteCompleted.count shouldBe 0L

        dao.getItemsForSessionSync(sessionId) shouldBe survivors
    }

    private fun onBeforeMoveToNext(cursor: Cursor) {
        if (cursor !is AbstractWindowedCursor) return
        val window = cursor.window ?: return
        val nextPosition = cursor.position + 1
        if (nextPosition != window.startPosition + window.numRows) return
        if (nextPosition >= cursor.count) return
        if (!armed.compareAndSet(true, false)) return

        hookFires.incrementAndGet()
        refillRequiredRow.set(nextPosition)
        refillStartRow.set(nextPosition - window.numRows / 3)
        log(TAG) { "Next move leaves window [${window.startPosition}, +${window.numRows}), starting delete" }
        writer.set(
            writerExecutor.submit {
                writerStarted.countDown()
                runBlocking {
                    dao.deleteByDecisions(sessionId, listOf(SwipeDecision.DELETED, SwipeDecision.KEEP))
                }
                deleteCompleted.countDown()
            }
        )
        writerStartedBeforeRefill.set(writerStarted.await(HOOK_WAIT_SECONDS, TimeUnit.SECONDS))
        deleteCompletedBeforeRefill.set(deleteCompleted.await(HOOK_WAIT_SECONDS, TimeUnit.SECONDS))
    }

    private fun SupportSQLiteQuery.isSwipeItemsSelect(): Boolean =
        sql.trimStart().startsWith("SELECT", ignoreCase = true) && sql.contains("FROM swipe_items")

    private inner class RefillHookCursor(private val delegate: Cursor) : CursorWrapper(delegate) {
        override fun moveToNext(): Boolean {
            onBeforeMoveToNext(delegate)
            return super.moveToNext()
        }
    }

    private inner class RefillHookDatabase(
        private val delegate: SupportSQLiteDatabase,
    ) : SupportSQLiteDatabase by delegate {
        override fun query(query: SupportSQLiteQuery): Cursor = delegate.query(query).hooked(query)

        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor =
            delegate.query(query, cancellationSignal).hooked(query)

        private fun Cursor.hooked(query: SupportSQLiteQuery): Cursor =
            if (query.isSwipeItemsSelect()) RefillHookCursor(this) else this
    }

    private inner class RefillHookOpenHelper(
        private val delegate: SupportSQLiteOpenHelper,
    ) : SupportSQLiteOpenHelper by delegate {
        override val writableDatabase: SupportSQLiteDatabase
            get() = RefillHookDatabase(delegate.writableDatabase)

        override val readableDatabase: SupportSQLiteDatabase
            get() = RefillHookDatabase(delegate.readableDatabase)
    }

    private inner class RefillHookOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
        private val delegate = FrameworkSQLiteOpenHelperFactory()

        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
            RefillHookOpenHelper(delegate.create(configuration))
    }

    companion object {
        private val TAG = logTag("Swiper", "Test", "WindowRefill")

        private const val ITEM_COUNT = 3000
        private const val PATH_PADDING = 2000
        private const val SURVIVOR_CYCLE = 15
        private const val SURVIVORS_PER_CYCLE = 4
        private const val HOOK_WAIT_SECONDS = 5L
        private const val WRITER_JOIN_TIMEOUT_SECONDS = 15L
    }
}
