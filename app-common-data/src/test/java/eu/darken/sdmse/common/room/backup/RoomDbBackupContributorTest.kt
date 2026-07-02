package eu.darken.sdmse.common.room.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.backup.DatabaseSchemaMismatchException
import eu.darken.sdmse.common.backup.RestoreMode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
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

    /**
     * Creates a DB shaped like Room's output: the payload table plus `room_master_table` carrying
     * [identityHash] (or none at all when `null`, mimicking a non-Room SQLite file).
     */
    private fun openDb(
        name: String,
        withGhostColumn: Boolean = false,
        identityHash: String? = "hash-v1",
    ): SupportSQLiteOpenHelper {
        val ddl = "CREATE TABLE t (id INTEGER PRIMARY KEY, txt TEXT" + (if (withGhostColumn) ", ghost TEXT" else "") + ")"
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(ddl)
                    if (identityHash != null) {
                        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                        db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$identityHash')")
                    }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    private fun contributor() = TestContributor({ db }, { ctx.getDatabasePath("live.db") }, listOf("t"))

    /** Builds a standalone backup DB file and returns its path. */
    private fun buildBackupDb(
        name: String,
        withGhostColumn: Boolean = false,
        identityHash: String? = "hash-v1",
        rows: Map<Long, String?> = emptyMap(),
    ): File {
        val otherHelper = openDb(name, withGhostColumn = withGhostColumn, identityHash = identityHash)
        rows.forEach { (id, txt) ->
            otherHelper.writableDatabase.insert(
                "t", SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("id", id)
                    if (txt != null) put("txt", txt) else putNull("txt")
                    if (withGhostColumn) put("ghost", "dropme")
                },
            )
        }
        otherHelper.close()
        return ctx.getDatabasePath(name)
    }

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
    fun `merge adds non-colliding rows and never overwrites local data`() = runTest {
        db.insertRow(1, "fromBackup")
        db.insertRow(3, "alsoBackup")

        val backup = File.createTempFile("backup-", ".db")
        try {
            contributor().exportTo(backup)

            db.execSQL("DELETE FROM t")
            db.insertRow(1, "local")     // collides with backup id=1
            db.insertRow(2, "localOnly") // unrelated local row

            contributor().restoreFrom(backup, RestoreMode.MERGE)

            // INSERT OR IGNORE: id=1 stays local (collision ignored), id=2 untouched, id=3 added.
            rowsById() shouldBe mapOf(1L to "local", 2L to "localOnly", 3L to "alsoBackup")
        } finally {
            backup.delete()
        }
    }

    @Test
    fun `validate accepts a schema-identical backup`() = runTest {
        val backup = buildBackupDb("other.db", rows = mapOf(5L to "ok"))
        contributor().validate(backup)
    }

    @Test
    fun `restore rejects drifted columns even when the identity hash matches`() = runTest {
        // A tampered file can carry a matching room_master_table while its actual tables drifted —
        // the structural comparison must catch what the hash can't prove.
        db.insertRow(1, "keep")
        val backup = buildBackupDb("other.db", withGhostColumn = true, rows = mapOf(5L to "ok"))

        shouldThrow<DatabaseSchemaMismatchException> {
            contributor().restoreFrom(backup, RestoreMode.REPLACE)
        }
        rowsById() shouldBe mapOf(1L to "keep")
    }

    @Test
    fun `restore rejects a backup with a different identity hash`() = runTest {
        db.insertRow(1, "keep")
        val backup = buildBackupDb("other.db", identityHash = "hash-v2", rows = mapOf(5L to "ok"))

        shouldThrow<DatabaseSchemaMismatchException> {
            contributor().restoreFrom(backup, RestoreMode.REPLACE)
        }
        rowsById() shouldBe mapOf(1L to "keep")
    }

    @Test
    fun `restore rejects a non-Room database`() = runTest {
        db.insertRow(1, "keep")
        val backup = buildBackupDb("other.db", identityHash = null, rows = mapOf(5L to "ok"))

        shouldThrow<DatabaseSchemaMismatchException> {
            contributor().restoreFrom(backup, RestoreMode.REPLACE)
        }
        rowsById() shouldBe mapOf(1L to "keep")
    }

    @Test
    fun `restore rejects a file that is not SQLite at all`() = runTest {
        db.insertRow(1, "keep")
        val garbage = File.createTempFile("garbage-", ".db").apply { writeText("this is not a database") }

        try {
            shouldThrowAny {
                contributor().restoreFrom(garbage, RestoreMode.REPLACE)
            }
            rowsById() shouldBe mapOf(1L to "keep")
        } finally {
            garbage.delete()
        }
    }
}
