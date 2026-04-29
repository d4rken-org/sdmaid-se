package eu.darken.sdmse.squeezer.core.processor

import dagger.Reusable
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Atomic file replacement for the squeezer processors — same-volume rename swap with backup
 * recovery, orphan cleanup, and restore-failure safety.
 *
 * TODO(gateway): FileTransaction operates on [File] because the transcode step requires a
 * raw filesystem path (Media3 Transformer) or a raw file path / stream (BitmapFactory +
 * ExifPreserver), and the atomic swap at the end is a same-volume `File.renameTo`.
 * MediaScanner and the processors pre-filter candidates via `SqueezerEligibility` at
 * `LocalGateway.Mode.NORMAL` on a best-effort basis — `renameTo` can still fail if the
 * filesystem state changes after preflight (a race, a volume remount, or a permission
 * flip). When Media3 ships a ParcelFileDescriptor / stream-backed output API, this layer
 * should move up to `APath` and `GatewaySwitch.rename` / `GatewaySwitch.delete` so free
 * NORMAL → ROOT → ADB escalation flows through from LocalGateway.
 */
@Reusable
class FileTransaction @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val fileOps: FileOps,
) {

    data class Outcome(
        val originalSize: Long,
        val replacementSize: Long,
        val savedBytes: Long,
        val replaced: Boolean,
    )

    /**
     * Atomically replace [target] with content written by [produce].
     *
     * Workflow:
     * 1. Recover any orphan backup/temp files from a prior interrupted run.
     * 2. Run [produce] to write the new content to a temp file inside a hidden workdir.
     * 3. If the new content is not smaller than the original, abort without touching the original.
     * 4. If [dryRun] is true, skip the swap and report what would have been saved.
     * 5. Otherwise, rename original → backup, rename temp → original, verify, delete backup.
     *
     * Safety contract:
     * - The only recoverable copy of the user's data (the backup) is **never** deleted on an
     *   error path where the original has not been positively restored or verified.
     * - Interrupted runs leave orphans in the workdir; they are reconciled on the next
     *   invocation targeting the same file.
     *
     * @throws IOException if produce() fails to write the temp file, if the swap fails and the
     *   backup can be restored, or if restore itself fails. In the restore-failure case the
     *   backup is left on disk for manual recovery.
     */
    suspend fun replace(
        target: File,
        dryRun: Boolean,
        produce: suspend (tempFile: File) -> Unit,
    ): Outcome = withContext(dispatcherProvider.IO) {
        val workDir = workDirFor(target)
        ensureWorkDir(workDir)
        recoverOrphansFor(target, workDir)

        val tempFile = tempFileFor(target, workDir)
        val backupFile = backupFileFor(target, workDir)

        // Start from a known-clean slate for this target (orphan recovery above already
        // handled anything that required recovery; anything still here is stale).
        fileOps.delete(tempFile)
        fileOps.delete(backupFile)

        val originalSize = fileOps.length(target)

        try {
            produce(tempFile)

            if (!fileOps.exists(tempFile) || !fileOps.canRead(tempFile)) {
                throw IOException("Produce step did not create readable temp file: ${tempFile.path}")
            }

            val compressedSize = fileOps.length(tempFile)

            if (compressedSize <= 0 || compressedSize >= originalSize) {
                log(TAG, VERBOSE) { "No savings for ${target.path} ($compressedSize >= $originalSize)" }
                fileOps.delete(tempFile)
                return@withContext Outcome(
                    originalSize = originalSize,
                    replacementSize = compressedSize,
                    savedBytes = 0,
                    replaced = false,
                )
            }

            if (dryRun) {
                log(TAG, INFO) { "DRYRUN: would save ${originalSize - compressedSize} bytes on ${target.path}" }
                fileOps.delete(tempFile)
                return@withContext Outcome(
                    originalSize = originalSize,
                    replacementSize = compressedSize,
                    savedBytes = originalSize - compressedSize,
                    replaced = false,
                )
            }

            // Move original out of the way into the backup slot. If this rename fails, we've
            // touched nothing and bail.
            if (!fileOps.renameTo(target, backupFile)) {
                throw IOException("Failed to create backup at ${backupFile.path}")
            }

            try {
                // Preserve the original mtime so gallery apps that sort by date don't reorder
                // compressed files (issue #2388). Set it on the temp inode before the final
                // rename: rename preserves inode metadata, so the timestamp carries through.
                // Doing it post-rename would leave a process-death window where orphan recovery
                // deletes the backup but target still has the "now" mtime.
                val originalModifiedAt = fileOps.getLastModified(backupFile)
                if (originalModifiedAt > 0L) {
                    if (!fileOps.setLastModified(tempFile, originalModifiedAt)) {
                        log(TAG, WARN) {
                            "Failed to preserve modification date for ${target.path}"
                        }
                    }
                }

                // Atomic same-volume rename. The workdir sits next to target so this should
                // always succeed. If it doesn't, fail closed — a copy fallback would risk
                // data loss if the app dies mid-copy (orphan recovery can't distinguish a
                // partial copy from a valid file).
                if (!fileOps.renameTo(tempFile, target)) {
                    throw IOException("Failed to rename temp to target: ${target.path}")
                }

                if (!fileOps.exists(target) ||
                    !fileOps.canRead(target) ||
                    fileOps.length(target) != compressedSize
                ) {
                    throw IOException(
                        "Verification failed: expected $compressedSize bytes at ${target.path}"
                    )
                }

            } catch (swapError: Throwable) {
                log(TAG, WARN) { "Swap failed, restoring backup: ${swapError.message}" }
                // Ensure the target slot is empty before we rename back.
                if (fileOps.exists(target)) fileOps.delete(target)
                if (!fileOps.renameTo(backupFile, target)) {
                    // Restore failed. DO NOT delete the backup — it is the only remaining copy.
                    log(TAG, ERROR) {
                        "CRITICAL: restore failed; backup preserved at ${backupFile.path}"
                    }
                    throw IOException(
                        "Failed to restore backup after swap failure; backup preserved at ${backupFile.path}",
                        swapError,
                    )
                }
                throw swapError
            }

            // Swap verified. Backup is now redundant and safe to remove.
            if (!fileOps.delete(backupFile)) {
                log(TAG, WARN) { "Post-swap backup delete failed: ${backupFile.path}" }
            }

            return@withContext Outcome(
                originalSize = originalSize,
                replacementSize = compressedSize,
                savedBytes = originalSize - compressedSize,
                replaced = true,
            )
        } catch (e: Throwable) {
            // Best-effort cleanup of the temp file. Never touch the backup here — if a swap
            // happened and then failed, the inner catch already handled restore; if no swap
            // happened, there is no backup to worry about.
            withContext(NonCancellable) {
                if (fileOps.exists(tempFile)) fileOps.delete(tempFile)
            }
            throw e
        } finally {
            withContext(NonCancellable) {
                cleanupWorkDirIfEmpty(workDir)
            }
        }
    }

    /**
     * Reconcile leftover files from a prior interrupted run targeting [target]:
     *
     * - If a `backup_<name>` exists and [target] is missing or zero-length, restore the backup
     *   in place. Process death after `rename(target → backup)` but before
     *   `rename(temp → target)` is the scenario this catches.
     * - If a `backup_<name>` exists alongside an apparently-intact target, the swap completed
     *   but the cleanup step was interrupted — delete the stale backup.
     * - Orphan temp files are always safe to discard.
     */
    private suspend fun recoverOrphansFor(target: File, workDir: File) {
        if (!fileOps.exists(workDir)) return

        val backupFile = backupFileFor(target, workDir)
        val tempFile = tempFileFor(target, workDir)

        if (fileOps.exists(backupFile)) {
            val targetMissing = !fileOps.exists(target) || fileOps.length(target) == 0L
            if (targetMissing) {
                log(TAG, INFO) { "Recovering orphan backup: ${backupFile.path} -> ${target.path}" }
                if (!fileOps.renameTo(backupFile, target)) {
                    try {
                        fileOps.copyFile(backupFile, target)
                        fileOps.delete(backupFile)
                        log(TAG, INFO) { "Recovered orphan backup via copy fallback" }
                    } catch (e: Throwable) {
                        log(TAG, ERROR) { "Failed to recover orphan backup: ${e.asLog()}" }
                    }
                }
            } else {
                log(TAG, INFO) { "Removing stale orphan backup: ${backupFile.path}" }
                fileOps.delete(backupFile)
            }
        }

        if (fileOps.exists(tempFile)) {
            log(TAG, INFO) { "Removing orphan temp: ${tempFile.path}" }
            fileOps.delete(tempFile)
        }
    }

    private suspend fun ensureWorkDir(workDir: File) {
        if (!fileOps.exists(workDir)) {
            if (!fileOps.mkdirs(workDir) && !fileOps.exists(workDir)) {
                throw IOException("Failed to create workdir: ${workDir.path}")
            }
        }
        val nomedia = File(workDir, ".nomedia")
        if (!fileOps.exists(nomedia)) {
            fileOps.createFile(nomedia)
        }
    }

    private suspend fun cleanupWorkDirIfEmpty(workDir: File) {
        if (!fileOps.exists(workDir)) return
        val entries = fileOps.listFiles(workDir)
        val meaningful = entries.filter { it.name != ".nomedia" }
        if (meaningful.isEmpty()) {
            entries.forEach { fileOps.delete(it) }
            fileOps.delete(workDir)
        }
    }

    private fun workDirFor(target: File): File = File(target.parentFile, WORKDIR_NAME)

    private fun tempFileFor(target: File, workDir: File): File =
        File(workDir, "$TEMP_PREFIX${target.name}")

    private fun backupFileFor(target: File, workDir: File): File =
        File(workDir, "$BACKUP_PREFIX${target.name}")

    companion object {
        const val WORKDIR_NAME = ".sdmaid_squeezer"
        const val TEMP_PREFIX = "compress_"
        const val BACKUP_PREFIX = "backup_"
        private val TAG = logTag("Squeezer", "FileTransaction")
    }
}
