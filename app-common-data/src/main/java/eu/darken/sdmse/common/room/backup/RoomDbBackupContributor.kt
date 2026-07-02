package eu.darken.sdmse.common.room.backup

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.darken.sdmse.common.backup.DatabaseBackupContributor
import eu.darken.sdmse.common.backup.DatabaseSchemaMismatchException
import eu.darken.sdmse.common.backup.InvalidBackupException
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
 * Restore only accepts a backup that provably has the installed schema (see [validate]);
 * rows are then copied verbatim with `INSERT … SELECT` per table inside one transaction, so a failed
 * restore never leaves the live database partially modified. ATTACH/DETACH run outside the
 * transaction (SQLite requirement).
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

    /**
     * Probes [source] on its own standalone connection and compares its schema to the live database.
     * Never ATTACHes an unvalidated file to the live connection: SQLite reports a garbage/corrupt
     * attach target as connection-level corruption, which trips Android's corruption handler against
     * the LIVE database — the standalone probe keeps an untrusted file completely isolated.
     */
    override suspend fun validate(source: File) {
        val backup = probeBackup(source)
        compareToLive(sqliteProvider(), backup)
    }

    override suspend fun restoreFrom(source: File, mode: RestoreMode) {
        // Validated again even though the manager preflights — restoreFrom must be safe on its own.
        // Only a proven-healthy SQLite file with the exact live schema is ever ATTACHed.
        validate(source)
        val db = sqliteProvider()
        log(TAG) { "restoreFrom($key, mode=$mode)" }
        db.execSQL("ATTACH DATABASE ? AS backup_src", arrayOf(source.absolutePath))
        try {
            db.beginTransaction()
            try {
                var total = 0
                tables.forEach { table ->
                    // Schema equality is proven, so all live columns are copied verbatim.
                    val cols = columns(db, "main", table).joinToString(", ") { "`$it`" }
                    if (mode == RestoreMode.REPLACE) db.execSQL("DELETE FROM main.`$table`")
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

    private class ProbedSchema(
        val identityHash: String,
        val columnsByTable: Map<String, List<String>>,
    )

    /**
     * Opens [source] read-only on a throwaway connection (no-op error handler, so the framework
     * never auto-deletes a file it deems corrupt) and extracts everything the comparison needs:
     * Room identity hash, per-table columns, and a `quick_check` verdict.
     */
    private fun probeBackup(source: File): ProbedSchema {
        val backupDb = try {
            SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY) { /* no-op */ }
        } catch (e: Exception) {
            throw InvalidBackupException("Backup database '$key' is not a valid SQLite database", e)
        }
        backupDb.use { bdb ->
            val check = try {
                bdb.rawQuery("PRAGMA quick_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (e: Exception) {
                throw InvalidBackupException("Backup database '$key' is not a valid SQLite database", e)
            }
            if (check != "ok") {
                throw InvalidBackupException("Backup database '$key' failed integrity check: $check")
            }
            val hash = try {
                bdb.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (e: Exception) {
                null // No room_master_table → not a Room database.
            } ?: throw DatabaseSchemaMismatchException(key, "backup is not a Room database")
            val columnsByTable = tables.associateWith { table ->
                val out = mutableListOf<String>()
                bdb.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    if (nameIdx >= 0) while (c.moveToNext()) out += c.getString(nameIdx)
                }
                out.toList()
            }
            return ProbedSchema(hash, columnsByTable)
        }
    }

    /**
     * The backup must prove it has exactly the installed schema before a single row is copied: the
     * same Room identity hash and identical table + column sets. The hash alone is not enough —
     * `room_master_table` is plain data a tampered file could carry while its actual tables drifted —
     * hence the explicit structural comparison. Reads only, never mutates either database.
     */
    private fun compareToLive(db: SupportSQLiteDatabase, backup: ProbedSchema) {
        val liveHash = try {
            db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        } ?: throw DatabaseSchemaMismatchException(key, "live database has no Room identity hash")
        if (liveHash != backup.identityHash) {
            throw DatabaseSchemaMismatchException(key, "identity hash mismatch (${backup.identityHash} vs $liveHash)")
        }
        tables.forEach { table ->
            val live = columns(db, "main", table)
            if (live.isEmpty()) throw DatabaseSchemaMismatchException(key, "table '$table' missing in live database")
            val backupCols = backup.columnsByTable[table].orEmpty()
            if (live.toSet() != backupCols.toSet()) {
                throw DatabaseSchemaMismatchException(key, "column mismatch for '$table' ($backupCols vs $live)")
            }
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
