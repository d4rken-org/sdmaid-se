package eu.darken.sdmse.common.debug.recorder.core

import java.io.File
import java.time.Instant

sealed interface DebugLogSession {
    val id: String
    val createdAt: Instant
    val logDir: File
    val diskSize: Long

    data class Recording(
        override val id: String,
        override val createdAt: Instant,
        override val logDir: File,
        override val diskSize: Long,
    ) : DebugLogSession

    data class Zipping(
        override val id: String,
        override val createdAt: Instant,
        override val logDir: File,
        override val diskSize: Long,
    ) : DebugLogSession

    data class Finished(
        override val id: String,
        override val createdAt: Instant,
        override val logDir: File,
        override val diskSize: Long,
        val zipFile: File,
        val compressedSize: Long,
    ) : DebugLogSession

    data class Failed(
        override val id: String,
        override val createdAt: Instant,
        override val logDir: File,
        override val diskSize: Long,
        val reason: Reason,
    ) : DebugLogSession {
        enum class Reason { EMPTY_LOG, MISSING_LOG, CORRUPT_ZIP, ZIP_FAILED }
    }
}
