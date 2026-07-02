package eu.darken.sdmse.common.backup

import java.io.File

/**
 * Backs up a whole SQLite database as a file (not row-by-row JSON), so memory stays flat regardless
 * of row count — the right approach for potentially large history tables.
 *
 * The backup archive is a zip: settings/content go into `config.json`, and each database goes in as
 * its own `databases/<key>` entry. Export clones the live DB to a file; restore re-attaches that file
 * and copies rows at the SQLite level.
 */
interface DatabaseBackupContributor {

    /** Stable id, also the zip entry name under `databases/`. */
    val key: String

    /** Write a standalone, consistent copy of the live database into [target]. */
    suspend fun exportTo(target: File)

    /**
     * Verify [source] can be applied to the live database, without writing anything. Called during
     * the restore preflight — before any section or database is mutated — and again by [restoreFrom]
     * itself. Throws (e.g. [DatabaseSchemaMismatchException]) when the backup is incompatible.
     */
    suspend fun validate(source: File)

    /** Restore from a previously exported database file. MERGE upserts by PK; REPLACE clears first. */
    suspend fun restoreFrom(source: File, mode: RestoreMode)
}
