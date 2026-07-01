package eu.darken.sdmse.squeezer.core

import kotlinx.coroutines.CancellationException
import java.io.IOException

enum class FailureReason {
    INSUFFICIENT_STORAGE,
    CODEC_UNSUPPORTED,
    METADATA_UNPRESERVABLE,
    IO_ERROR,
    CANCELLED,
    UNKNOWN,
}

fun Throwable.toFailureReason(): FailureReason = when (this) {
    is InsufficientStorageException -> FailureReason.INSUFFICIENT_STORAGE
    is UnsupportedFormatException -> FailureReason.CODEC_UNSUPPORTED
    // Must precede the `is IOException` branch — MetadataPreservationException is a subclass.
    is MetadataPreservationException -> FailureReason.METADATA_UNPRESERVABLE
    is CancellationException -> FailureReason.CANCELLED
    is IOException -> FailureReason.IO_ERROR
    else -> FailureReason.UNKNOWN
}

class InsufficientStorageException(
    val requiredBytes: Long,
    val availableBytes: Long,
) : IOException("Insufficient storage: need $requiredBytes bytes, have $availableBytes bytes")

class UnsupportedFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * A source file's metadata (e.g. HEIC EXIF) is present but can't be extracted safely, so
 * compression is aborted rather than silently stripping the user's date/location/camera tags.
 * A deliberate protective failure — surfaced distinctly from a generic [IOException] so results
 * don't mislabel it as an "IO error". Subclass of [IOException] so [FileTransaction] still aborts
 * the replacement the same way.
 */
class MetadataPreservationException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
