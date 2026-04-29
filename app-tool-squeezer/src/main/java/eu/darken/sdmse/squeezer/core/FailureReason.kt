package eu.darken.sdmse.squeezer.core

import kotlinx.coroutines.CancellationException
import java.io.IOException

enum class FailureReason {
    INSUFFICIENT_STORAGE,
    CODEC_UNSUPPORTED,
    IO_ERROR,
    CANCELLED,
    UNKNOWN,
}

fun Throwable.toFailureReason(): FailureReason = when (this) {
    is InsufficientStorageException -> FailureReason.INSUFFICIENT_STORAGE
    is UnsupportedFormatException -> FailureReason.CODEC_UNSUPPORTED
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
