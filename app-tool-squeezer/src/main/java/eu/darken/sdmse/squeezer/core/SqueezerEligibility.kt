package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import java.io.File

/**
 * Single source of truth for "can squeezer actually process this file?".
 *
 * Media3 Transformer and BitmapFactory both require a raw [java.io.File]-backed path, so any
 * candidate that can't be opened via normal java.io.File APIs would fail at transcode time
 * regardless of gateway escalation. Rather than scan / preview / process independently and
 * fail late, all three call [check] early and consistently.
 *
 * This is a best-effort stat check — the filesystem can change between the check and the
 * actual transcode. The processors call [check] again as a preflight to catch that drift.
 */
object SqueezerEligibility {

    enum class Verdict {
        /** Ready to process via java.io.File. */
        OK,

        /** Not a [LocalPath] — SAF roots must be normalized upstream via PathMapper. */
        NOT_LOCAL,

        /** Path doesn't resolve to a regular file (directory, missing, special file). */
        NOT_A_FILE,

        /** File exists but can't be read via normal java.io.File. */
        UNREADABLE,

        /**
         * Parent directory isn't writable — blocks creating `.sdmaid_squeezer/` and the
         * atomic rename swap. On Linux, rename requires write on the parent, not the file.
         */
        PARENT_NOT_WRITABLE,
    }

    fun check(file: File): Verdict = when {
        !file.isFile -> Verdict.NOT_A_FILE
        !file.canRead() -> Verdict.UNREADABLE
        file.parentFile?.canWrite() != true -> Verdict.PARENT_NOT_WRITABLE
        else -> Verdict.OK
    }

    fun check(path: APath?): Verdict {
        val localPath = path as? LocalPath ?: return Verdict.NOT_LOCAL
        return check(File(localPath.path))
    }
}
