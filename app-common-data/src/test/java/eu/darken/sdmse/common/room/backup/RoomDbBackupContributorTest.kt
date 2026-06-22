package eu.darken.sdmse.common.room.backup

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.backup.RestoreMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RoomDbBackupContributorTest : BaseTest() {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase
    private val json = Json

    private class TestContributor(
        provider: () -> SupportSQLiteDatabase,
        tables: List<String>,
    ) : RoomDbBackupContributor(provider, tables) {
        override val key = "test.db"
    }

    private fun contributor() = TestContributor({ db }, listOf("t"))

    @Before
    fun setup() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY, txt TEXT, rl REAL, bl BLOB, nl TEXT)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private fun insert(id: Long, txt: String?, rl: Double?, bl: ByteArray?, nl: String?) {
        val cv = ContentValues().apply {
            put("id", id)
            if (txt != null) put("txt", txt) else putNull("txt")
            if (rl != null) put("rl", rl) else putNull("rl")
            if (bl != null) put("bl", bl) else putNull("bl")
            if (nl != null) put("nl", nl) else putNull("nl")
        }
        db.insert("t", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    private fun rows(): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        db.query("SELECT * FROM t ORDER BY id").use { c ->
            while (c.moveToNext()) {
                val row = mutableMapOf<String, Any?>()
                for (i in 0 until c.columnCount) {
                    row[c.getColumnName(i)] = when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        android.database.Cursor.FIELD_TYPE_STRING -> c.getString(i)
                        android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(i).toList()
                        else -> null
                    }
                }
                out += row
            }
        }
        return out
    }

    @Test
    fun `replace round-trip preserves all column types incl blob and null`() = runTest {
        insert(1, "hello", 2.5, byteArrayOf(1, 2, 3), "extra")
        insert(2, null, null, null, null)

        val snap = contributor().snapshot()!!

        db.execSQL("DELETE FROM t")
        insert(99, "stale", 1.0, null, null)

        contributor().restore(snap, RestoreMode.REPLACE)

        val result = rows()
        result.map { it["id"] } shouldBe listOf(1L, 2L)
        result[0]["txt"] shouldBe "hello"
        result[0]["rl"] shouldBe 2.5
        result[0]["bl"] shouldBe listOf<Any?>(1L, 2L, 3L).map { (it as Long).toByte() }
        result[0]["nl"] shouldBe "extra"
        result[1]["txt"] shouldBe null
        result[1]["bl"] shouldBe null
    }

    @Test
    fun `merge upserts by primary key and keeps unrelated rows`() = runTest {
        insert(1, "fromBackup", null, null, null)
        val snap = contributor().snapshot()!!

        // Local divergence after the snapshot.
        insert(1, "localModified", null, null, null)
        insert(2, "localOnly", null, null, null)

        contributor().restore(snap, RestoreMode.MERGE)

        val byId = rows().associateBy { it["id"] }
        byId[1L]!!["txt"] shouldBe "fromBackup" // backup wins on PK
        byId[2L]!!["txt"] shouldBe "localOnly"  // untouched
    }

    @Test
    fun `snapshot of an empty database is null`() = runTest {
        contributor().snapshot() shouldBe null
    }

    // Golden DB-section format: if this stops parsing, the on-disk DB backup format changed.
    @Test
    fun `golden db section restores`() = runTest {
        val golden = """
            {"t":[{"id":{"t":"i","v":7},"txt":{"t":"s","v":"golden"},"rl":{"t":"r","v":1.5},"bl":{"t":"b","v":"AQID"},"nl":{"t":"0","v":null}}]}
        """.trimIndent()
        val data: JsonElement = json.parseToJsonElement(golden)

        contributor().restore(data, RestoreMode.REPLACE)

        val row = rows().single()
        row["id"] shouldBe 7L
        row["txt"] shouldBe "golden"
        row["rl"] shouldBe 1.5
        row["bl"] shouldBe byteArrayOf(1, 2, 3).toList()
        row["nl"] shouldBe null
    }

    // A column present in the backup but not in the live schema must be skipped, not crash.
    @Test
    fun `unknown column in backup is ignored`() = runTest {
        val withGhost = """
            {"t":[{"id":{"t":"i","v":3},"txt":{"t":"s","v":"ok"},"ghost":{"t":"s","v":"dropme"}}]}
        """.trimIndent()

        contributor().restore(json.parseToJsonElement(withGhost), RestoreMode.REPLACE)

        val row = rows().single()
        row["id"] shouldBe 3L
        row["txt"] shouldBe "ok"
    }
}
