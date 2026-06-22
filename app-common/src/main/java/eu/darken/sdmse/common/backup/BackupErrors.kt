package eu.darken.sdmse.common.backup

import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

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
