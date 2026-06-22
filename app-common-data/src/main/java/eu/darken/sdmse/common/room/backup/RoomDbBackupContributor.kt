package eu.darken.sdmse.common.room.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Generic [ConfigBackupContributor] base for Room databases. Dumps each named table row-by-row at the
 * raw SQLite level (`SELECT *` → per-cell `{t,v}` tag), so it is agnostic to Room entities and type
 * converters and needs no `@Serializable` annotations on entities. Restore writes through the live DB
 * connection (no file swap / app restart). Runs at [ORDER_CONTENT][ConfigBackupContributor.ORDER_CONTENT].
 *
 * Restore semantics: REPLACE clears each table first; MERGE upserts by primary key
 * (`CONFLICT_REPLACE`). Original primary keys are preserved so cross-table relations (e.g. a stats
 * report and its affected paths) stay intact. Only columns that still exist in the live schema are
 * written, so a column added/removed across versions doesn't fail the insert.
 *
 * [tables] must be ordered parents-first (no SQLite FK constraints are declared, but it keeps logical
 * relations tidy).
 */
abstract class RoomDbBackupContributor(
    private val database: RoomDatabase,
    private val tables: List<String>,
) : ConfigBackupContributor {

    override val restoreOrder = ConfigBackupContributor.ORDER_CONTENT

    override suspend fun snapshot(): JsonElement? {
        val db = database.openHelper.writableDatabase
        var total = 0
        val payload = buildJsonObject {
            tables.forEach { table ->
                val rows = dumpTable(db, table)
                total += rows.size
                put(table, JsonArray(rows))
            }
        }
        log(TAG) { "snapshot($key): $total rows across ${tables.size} tables" }
        return if (total == 0) null else payload
    }

    override suspend fun restore(data: JsonElement, mode: RestoreMode) {
        val db = database.openHelper.writableDatabase
        val payload = data.jsonObject
        log(TAG) { "restore($key, mode=$mode)" }
        db.beginTransaction()
        try {
            tables.forEach { table ->
                val rows = (payload[table] as? JsonArray) ?: return@forEach
                if (mode == RestoreMode.REPLACE) db.execSQL("DELETE FROM `$table`")
                val liveColumns = currentColumns(db, table)
                rows.forEach { row ->
                    val values = toContentValues(row.jsonObject, liveColumns)
                    if (values.size() > 0) {
                        db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun dumpTable(db: SupportSQLiteDatabase, table: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        db.query("SELECT * FROM `$table`").use { c ->
            while (c.moveToNext()) {
                out += buildJsonObject {
                    for (i in 0 until c.columnCount) {
                        put(c.getColumnName(i), c.cellToTagged(i))
                    }
                }
            }
        }
        return out
    }

    private fun Cursor.cellToTagged(i: Int): JsonObject = when (getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> tagged(TAG_INT, JsonPrimitive(getLong(i)))
        Cursor.FIELD_TYPE_FLOAT -> tagged(TAG_REAL, JsonPrimitive(getDouble(i)))
        Cursor.FIELD_TYPE_STRING -> tagged(TAG_TEXT, JsonPrimitive(getString(i)))
        Cursor.FIELD_TYPE_BLOB -> tagged(TAG_BLOB, JsonPrimitive(Base64.encodeToString(getBlob(i), Base64.NO_WRAP)))
        else -> tagged(TAG_NULL, JsonNull)
    }

    private fun tagged(tag: String, value: JsonElement) = buildJsonObject {
        put(KEY_TYPE, tag)
        put(KEY_VALUE, value)
    }

    private fun currentColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val cols = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            if (nameIdx < 0) return emptySet()
            while (c.moveToNext()) cols += c.getString(nameIdx)
        }
        return cols
    }

    private fun toContentValues(row: JsonObject, liveColumns: Set<String>): ContentValues {
        val cv = ContentValues()
        row.forEach { (column, cell) ->
            if (column !in liveColumns) return@forEach
            val obj = cell.jsonObject
            val tag = obj[KEY_TYPE]?.jsonPrimitive?.content
            val value = obj[KEY_VALUE]
            when (tag) {
                TAG_NULL -> cv.putNull(column)
                TAG_INT -> cv.put(column, value!!.jsonPrimitive.long)
                TAG_REAL -> cv.put(column, value!!.jsonPrimitive.double)
                TAG_TEXT -> cv.put(column, value!!.jsonPrimitive.content)
                TAG_BLOB -> cv.put(column, Base64.decode(value!!.jsonPrimitive.content, Base64.NO_WRAP))
            }
        }
        return cv
    }

    companion object {
        private val TAG = logTag("Backup", "RoomDbContributor")
        private const val KEY_TYPE = "t"
        private const val KEY_VALUE = "v"
        private const val TAG_NULL = "0"
        private const val TAG_INT = "i"
        private const val TAG_REAL = "r"
        private const val TAG_TEXT = "s"
        private const val TAG_BLOB = "b"
    }
}
