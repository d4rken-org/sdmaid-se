package eu.darken.sdmse.common.room.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import eu.darken.sdmse.common.backup.DatabaseBackupContributor
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

/**
 * Generic [DatabaseBackupContributor] for a Room database, working at the SQLite/file level so memory
 * stays flat regardless of row count.
 *
 * Export: WAL-checkpoint (TRUNCATE) so the main DB file is self-consistent, then copy that file. The
 * checkpoint result is verified and retried; if it can never complete (DB stays busy) the export fails
 * loudly rather than copying a file that is missing the latest WAL frames.
 * (`VACUUM INTO` would be cleaner but needs SQLite 3.27 / API 30; minSdk here is 26.)
 *
 * Restore: `ATTACH` the backup file and copy rows with `INSERT … SELECT` per table inside one
 * transaction. Only columns present in both schemas are copied, so a column added/removed across
 * versions is tolerated. ATTACH/DETACH run outside the transaction (SQLite requirement).
 * - REPLACE clears each table first, then a plain `INSERT` re-keys verbatim (original PKs preserved,
 *   collision-free) — the mode for a full device transfer.
 * - MERGE uses `INSERT OR IGNORE`: it adds backup rows that don't conflict with an existing row (by
 *   primary key or any UNIQUE/NOT NULL/CHECK constraint), never overwriting or deleting a local row.
 *   This honours MERGE's "never deletes existing data" contract and avoids the `INSERT OR REPLACE`
 *   hazards (clobbering an unrelated local row that shares an auto-increment id from another device, and
 *   cascade-deleting that row's FK children). It is best-effort: surrogate ids are not stable across
 *   devices, so colliding rows are skipped and a cross-device MERGE may import only part of a related
 *   set — REPLACE is the mode for a faithful, consistent history transfer.
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
        checkpoint(db)
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
                var total = 0
                tables.forEach { table ->
                    val live = columns(db, "main", table)
                    val backup = columns(db, "backup_src", table)
                    val shared = live.filter { it in backup }
                    if (shared.isEmpty()) {
                        log(TAG, WARN) { "restoreFrom($key): no shared columns for '$table', skipping" }
                        return@forEach
                    }
                    if (mode == RestoreMode.REPLACE) db.execSQL("DELETE FROM main.`$table`")
                    val cols = shared.joinToString(", ") { "`$it`" }
                    val verb = if (mode == RestoreMode.MERGE) "INSERT OR IGNORE" else "INSERT"
                    db.execSQL("$verb INTO main.`$table` ($cols) SELECT $cols FROM backup_src.`$table`")
                    val inserted = changes(db)
                    total += inserted
                    log(TAG) { "restoreFrom($key): $table +$inserted rows" }
                }
                db.setTransactionSuccessful()
                log(TAG) { "restoreFrom($key): $total rows across ${tables.size} tables" }
            } finally {
                db.endTransaction()
            }
        } finally {
            db.execSQL("DETACH DATABASE backup_src")
        }
    }

    /**
     * WAL-checkpoint in TRUNCATE mode so the main DB file holds every committed row before it is copied.
     * The pragma's first result column is the "busy" flag (non-zero = couldn't complete because of a
     * concurrent connection). Retry a few times, then fail rather than silently shipping a stale copy.
     */
    private suspend fun checkpoint(db: SupportSQLiteDatabase) {
        repeat(CHECKPOINT_ATTEMPTS) { attempt ->
            val busy = db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            if (busy == 0) return
            log(TAG, WARN) { "exportTo($key): WAL checkpoint busy (attempt ${attempt + 1}/$CHECKPOINT_ATTEMPTS)" }
            delay(CHECKPOINT_RETRY_MS)
        }
        throw IOException("WAL checkpoint did not complete for '$key'; database stayed busy")
    }

    private fun changes(db: SupportSQLiteDatabase): Int =
        db.query("SELECT changes()").use { if (it.moveToFirst()) it.getInt(0) else 0 }

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
        private const val CHECKPOINT_ATTEMPTS = 3
        private const val CHECKPOINT_RETRY_MS = 100L
    }
}
