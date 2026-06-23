package eu.darken.sdmse.common.room.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.backup.RestoreMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RoomDbBackupContributorTest : BaseTest() {

    private lateinit var ctx: Context
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    private class TestContributor(
        provider: () -> SupportSQLiteDatabase,
        fileProvider: () -> File,
        tables: List<String>,
    ) : RoomDbBackupContributor(provider, fileProvider, tables) {
        override val key = "test.db"
    }

    private fun openDb(name: String, withGhostColumn: Boolean = false): SupportSQLiteOpenHelper {
        val ddl = "CREATE TABLE t (id INTEGER PRIMARY KEY, txt TEXT" + (if (withGhostColumn) ", ghost TEXT" else "") + ")"
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(ddl)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun contributor() = TestContributor({ db }, { ctx.getDatabasePath("live.db") }, listOf("t"))

    @Before
    fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        helper = openDb("live.db")
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private fun SupportSQLiteDatabase.insertRow(id: Long, txt: String?) {
        insert("t", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
            put("id", id)
            if (txt != null) put("txt", txt) else putNull("txt")
        })
    }

    private fun rowsById(): Map<Long, String?> {
        val out = mutableMapOf<Long, String?>()
        db.query("SELECT id, txt FROM t ORDER BY id").use { c ->
            while (c.moveToNext()) {
                val txt = if (c.getType(1) == Cursor.FIELD_TYPE_NULL) null else c.getString(1)
                out[c.getLong(0)] = txt
            }
        }
        return out
    }

    @Test
    fun `replace round-trip restores the exported rows`() = runTest {
        db.insertRow(1, "a")
        db.insertRow(2, "b")

        val backup = File.createTempFile("backup-", ".db")
        try {
            contributor().exportTo(backup)

            db.execSQL("DELETE FROM t")
            db.insertRow(99, "stale")

            contributor().restoreFrom(backup, RestoreMode.REPLACE)

            rowsById() shouldBe mapOf(1L to "a", 2L to "b")
        } finally {
            backup.delete()
        }
    }

    @Test
    fun `merge upserts by primary key and keeps unrelated rows`() = runTest {
        db.insertRow(1, "fromBackup")

        val backup = File.createTempFile("backup-", ".db")
        try {
            contributor().exportTo(backup)

            db.execSQL("UPDATE t SET txt='local' WHERE id=1")
            db.insertRow(2, "localOnly")

            contributor().restoreFrom(backup, RestoreMode.MERGE)

            rowsById() shouldBe mapOf(1L to "fromBackup", 2L to "localOnly")
        } finally {
            backup.delete()
        }
    }

    @Test
    fun `restore tolerates a backup column missing from the live schema`() = runTest {
        // Build a separate backup DB whose table has an extra column.
        val otherHelper = openDb("other.db", withGhostColumn = true)
        otherHelper.writableDatabase.insert(
            "t", SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply { put("id", 5L); put("txt", "ok"); put("ghost", "dropme") },
        )
        otherHelper.close()
        val backup = ctx.getDatabasePath("other.db")

        contributor().restoreFrom(backup, RestoreMode.REPLACE)

        rowsById() shouldBe mapOf(5L to "ok")
    }
}
