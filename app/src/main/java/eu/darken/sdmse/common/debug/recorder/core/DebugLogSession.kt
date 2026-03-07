package eu.darken.sdmse.common.debug.recorder.core

import java.io.File
import java.time.Instant

sealed interface DebugLogSession {
    val id: String
    val createdAt: Instant
    val logDir: File

    data class Recording(
        override val id: String,
        override val createdAt: Instant,
        override val logDir: File,
    ) : DebugLogSession

    data class Zipping(
        override val id: String,
        override val createdAt: Instant,
        override val logDir: File,
    ) : DebugLogSession

    data class Finished(
        override val id: String,
        override val createdAt: Instant,
        override val logDir: File,
        val zipFile: File,
        val compressedSize: Long,
    ) : DebugLogSession
}
