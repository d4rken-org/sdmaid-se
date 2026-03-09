package eu.darken.sdmse.common.debug.recorder.core

import java.io.File
import java.time.Instant

@JvmInline
value class SessionId(val value: String) {
    val baseName: String get() = value.substringAfter(":")
    val location: String get() = value.substringBefore(":")

    override fun toString(): String = value

    companion object {
        fun derive(file: File): SessionId {
            val prefix = if (file.absolutePath.contains("/cache/debug/logs")) "cache" else "ext"
            val name = if (file.isDirectory) file.name else file.nameWithoutExtension
            return SessionId("$prefix:$name")
        }
    }
}

sealed interface DebugLogSession {
    val id: SessionId
    val createdAt: Instant
    val logDir: File
    val diskSize: Long

    data class Recording(
        override val id: SessionId,
        override val createdAt: Instant,
        override val logDir: File,
        override val diskSize: Long,
    ) : DebugLogSession

    data class Zipping(
        override val id: SessionId,
        override val createdAt: Instant,
        override val logDir: File,
        override val diskSize: Long,
    ) : DebugLogSession

    data class Finished(
        override val id: SessionId,
        override val createdAt: Instant,
        override val logDir: File,
        override val diskSize: Long,
        val zipFile: File,
        val compressedSize: Long,
    ) : DebugLogSession

    data class Failed(
        override val id: SessionId,
        override val createdAt: Instant,
        override val logDir: File,
        override val diskSize: Long,
        val reason: Reason,
    ) : DebugLogSession {
        enum class Reason { EMPTY_LOG, MISSING_LOG, CORRUPT_ZIP, ZIP_FAILED }
    }
}
