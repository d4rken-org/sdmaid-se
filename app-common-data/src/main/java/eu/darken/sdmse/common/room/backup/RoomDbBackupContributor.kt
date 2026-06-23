package eu.darken.sdmse.common.room.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import eu.darken.sdmse.common.backup.DatabaseBackupContributor
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File

/**
 * Generic [DatabaseBackupContributor] for a Room database, working at the SQLite/file level so memory
 * stays flat regardless of row count.
 *
 * Export: WAL-checkpoint (TRUNCATE) so the main DB file is self-consistent, then copy that file.
 * (`VACUUM INTO` would be cleaner but needs SQLite 3.27 / API 30; minSdk here is 26.)
 *
 * Restore: `ATTACH` the backup file and copy rows with `INSERT … SELECT` per table inside one
 * transaction — REPLACE clears each table first, MERGE upserts by primary key (`INSERT OR REPLACE`).
 * Only columns present in both schemas are copied, so a column added/removed across versions is
 * tolerated. ATTACH/DETACH run outside the transaction (SQLite requirement).
 *
 * [tables] should be ordered parents-first (no SQLite FK constraints are declared).
 */
abstract class RoomDbBackupContributor(
    private val sqliteProvider: () -> SupportSQLiteDatabase,
    private val dbFileProvider: () -> File,
    private val tables: List<String>,
) : DatabaseBackupContributor {

    override suspend fun exportTo(target: File) {
        val db = sqliteProvider()
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val source = dbFileProvider()
        source.copyTo(target, overwrite = true)
        log(TAG) { "exportTo($key): copied ${source.length()} bytes -> $target" }
    }

    override suspend fun restoreFrom(source: File, mode: RestoreMode) {
        val db = sqliteProvider()
        log(TAG) { "restoreFrom($key, mode=$mode)" }
        db.execSQL("ATTACH DATABASE ? AS backup_src", arrayOf(source.absolutePath))
        try {
            db.beginTransaction()
            try {
                tables.forEach { table ->
                    val live = columns(db, "main", table)
                    val backup = columns(db, "backup_src", table)
                    val shared = live.filter { it in backup }
                    if (shared.isEmpty()) return@forEach
                    if (mode == RestoreMode.REPLACE) db.execSQL("DELETE FROM main.`$table`")
                    val cols = shared.joinToString(", ") { "`$it`" }
                    val verb = if (mode == RestoreMode.MERGE) "INSERT OR REPLACE" else "INSERT"
                    db.execSQL("$verb INTO main.`$table` ($cols) SELECT $cols FROM backup_src.`$table`")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.execSQL("DETACH DATABASE backup_src")
        }
    }

    private fun columns(db: SupportSQLiteDatabase, schema: String, table: String): List<String> {
        val out = mutableListOf<String>()
        db.query("PRAGMA $schema.table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            if (nameIdx < 0) return emptyList()
            while (c.moveToNext()) out += c.getString(nameIdx)
        }
        return out
    }

    companion object {
        private val TAG = logTag("Backup", "RoomDbContributor")
    }
}
