package eu.darken.sdmse.common.backup

/**
 * Caps applied when reading a backup archive — untrusted, user-supplied input. Oversized input is
 * rejected with [InvalidBackupException] instead of exhausting memory, CPU, or cache storage
 * (zip-bomb defense). Values are deliberately generous; a real backup stays far below them.
 */
data class BackupLimits(
    /** Max number of entries in the archive. */
    val maxEntries: Int = 256,
    /** Max uncompressed size of a single entry. */
    val maxEntryBytes: Long = 256L * 1024 * 1024,
    /**
     * Max uncompressed size of a text entry (manifest/sections) — these get read into memory as a
     * whole, unlike databases, so their cap is much tighter.
     */
    val maxTextEntryBytes: Long = 16L * 1024 * 1024,
    /** Max total uncompressed size across all entries. */
    val maxTotalBytes: Long = 1024L * 1024 * 1024,
    /** Max (compressed) size of the archive file itself, checked while copying from SAF. */
    val maxArchiveBytes: Long = 1024L * 1024 * 1024,
)
