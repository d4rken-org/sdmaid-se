package eu.darken.sdmse.common.backup

import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import java.io.File

/** The backup file could not be parsed (corrupt, truncated, or not a backup at all). */
class InvalidBackupException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.backup_restore_error_invalid_title.toCaString(),
        description = R.string.backup_restore_error_invalid_body.toCaString(),
    )
}

/** The backup was written by a newer, incompatible format version than this app understands. */
class UnsupportedBackupVersionException(
    val backupVersion: Int,
) : IllegalStateException("Unsupported backup version: $backupVersion (max ${BackupEnvelope.VERSION})"),
    HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.backup_restore_error_invalid_title.toCaString(),
        description = R.string.backup_restore_error_version_body.toCaString(),
    )
}

/**
 * A restore was aborted (REPLACE: on the first failing section; MERGE: after applying everything).
 * [recoveryBackup] is the pre-restore safety snapshot, kept on failure so the previous configuration
 * can be restored — `null` only if the snapshot file went missing.
 */
class RestoreFailedException(
    val failedSections: List<String>,
    cause: Throwable? = null,
    val recoveryBackup: File? = null,
) : IllegalStateException("Restore failed for sections: $failedSections", cause), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.backup_restore_error_restore_failed_title.toCaString(),
        description = R.string.backup_restore_error_restore_failed_body.toCaString(
            failedSections.joinToString(", "),
        ),
    )
}

/**
 * The pre-restore safety snapshot of the current configuration could not be fully written, so the
 * restore was refused before touching anything — no complete safety net, no restore.
 */
class SafetyBackupFailedException(
    val failedSections: List<String> = emptyList(),
    cause: Throwable? = null,
) : IllegalStateException("Pre-restore safety backup incomplete (failed: $failedSections)", cause),
    HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.backup_restore_error_safety_title.toCaString(),
        description = R.string.backup_restore_error_safety_body.toCaString(),
    )
}

/** A backup or restore was refused because a cleaning task or another backup operation is running. */
class BackupBusyException :
    IllegalStateException("Backup/restore can't run while other operations are active"),
    HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.backup_restore_error_busy_title.toCaString(),
        description = R.string.backup_restore_error_busy_body.toCaString(),
    )
}

/** A backup database does not match the schema of the installed app version. */
class DatabaseSchemaMismatchException(
    dbKey: String,
    detail: String,
) : IllegalStateException("Schema mismatch for '$dbKey': $detail"), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.backup_restore_error_schema_title.toCaString(),
        description = R.string.backup_restore_error_schema_body.toCaString(),
    )
}
