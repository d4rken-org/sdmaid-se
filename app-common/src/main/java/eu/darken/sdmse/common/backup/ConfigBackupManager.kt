package eu.darken.sdmse.common.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.UpgradeRequiredException
import eu.darken.sdmse.common.upgrade.isProSettled
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads/writes a backup archive (a zip). Layout:
 * - `manifest.json` — the [BackupEnvelope] (provenance metadata).
 * - `sections/<key>.json` — one file per [ConfigBackupContributor] (settings + content).
 * - `databases/<key>` — one SQLite file per [DatabaseBackupContributor], copied at the file level so
 *   memory stays flat regardless of row count.
 *
 * Export is Pro-gated; import/restore is free. Backup operations are mutually exclusive with each
 * other and with tool tasks (see [BackupOperationGate]).
 */
@Singleton
class ConfigBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sectionContributors: Set<@JvmSuppressWildcards ConfigBackupContributor>,
    private val databaseContributors: Set<@JvmSuppressWildcards DatabaseBackupContributor>,
    private val json: Json,
    private val upgradeRepo: UpgradeRepo,
    private val envelopeSource: BackupEnvelopeSource,
    private val gate: BackupOperationGate,
    private val limits: BackupLimits,
) {

    private val tmpDir: File
        get() = File(context.cacheDir, "backup").apply { mkdirs() }

    // Safety snapshots must survive cache eviction (disk pressure is exactly when a restore is most
    // likely to fail), so they live in noBackupFilesDir, not cacheDir.
    private val safetyDir: File
        get() = File(context.noBackupFilesDir, "backup").apply { mkdirs() }

    // Backup entries are written pretty-printed so the archive is human-inspectable. Derived from the
    // injected json so it keeps the app's serializers module; parsing on restore stays on `json`.
    private val prettyJson = Json(from = json) { prettyPrint = true }

    /**
     * Streams a full backup zip into [out]. Throws [UpgradeRequiredException] for non-Pro users and
     * [BackupBusyException] while other work is running.
     *
     * Per-contributor failures are isolated (the rest of the archive is still written) and reported in
     * the returned [WriteResult] so callers can surface a partial backup instead of a false success.
     */
    suspend fun writeBackup(out: OutputStream): WriteResult = gate.runExclusive {
        if (!upgradeRepo.isProSettled()) {
            log(TAG, WARN) { "writeBackup() denied, Pro upgrade required" }
            throw UpgradeRequiredException()
        }
        writeArchive(out)
    }

    /** Ungated [writeBackup] body — also used for the pre-restore safety snapshot. */
    internal suspend fun writeArchive(out: OutputStream): WriteResult {
        log(TAG, INFO) { "writeArchive(): starting" }
        val result = ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(prettyJson.encodeToString(envelopeSource.create()).toByteArray())
            zip.closeEntry()
            writeEntries(zip)
        }
        log(TAG, INFO) { "writeArchive(): wrote ${result.written.size} entries ${result.written.sorted()}, ${result.failures.size} failures" }
        return result
    }

    /**
     * Writes every contributor's section + database entry into an already-open [zip] and returns the
     * per-entry outcome. Split out from [writeArchive] so it can be unit-tested without the manifest
     * write.
     */
    internal suspend fun writeEntries(zip: ZipOutputStream): WriteResult {
        val written = mutableSetOf<String>()
        val failures = mutableListOf<SectionFailure>()

        sectionContributors.sortedBy { it.key }.forEach { c ->
            val element = try {
                c.snapshot()
            } catch (e: Exception) {
                log(TAG, ERROR) { "snapshot() failed for '${c.key}': ${e.asLog()}" }
                failures.add(SectionFailure(c.key, e))
                null
            } ?: return@forEach
            zip.putNextEntry(ZipEntry("$SECTIONS_DIR${c.key}.json"))
            zip.write(prettyJson.encodeToString(element).toByteArray())
            zip.closeEntry()
            written += c.key
        }

        databaseContributors.sortedBy { it.key }.forEach { c ->
            val tmp = File.createTempFile("export-${c.key}-", ".db", tmpDir)
            try {
                // Only the contributor's own export is isolated as a per-entry failure. A failure while
                // writing to the zip itself must abort the whole backup — a half-written archive is not a
                // "partial" and must not be reported as success.
                val exported = try {
                    c.exportTo(tmp)
                    tmp.length() > 0
                } catch (e: Exception) {
                    log(TAG, ERROR) { "exportTo() failed for '${c.key}': ${e.asLog()}" }
                    failures.add(SectionFailure(c.key, e))
                    false
                }
                if (exported) {
                    zip.putNextEntry(ZipEntry("$DB_DIR${c.key}"))
                    tmp.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    written += c.key
                }
            } finally {
                tmp.delete()
            }
        }
        return WriteResult(written = written, failures = failures)
    }

    /** Parse + version-check `manifest.json` from a backup zip, without applying it. */
    fun parse(zip: File): BackupEnvelope = try {
        ZipFile(zip).use { zf ->
            val entry = zf.getEntry(MANIFEST_ENTRY)
                ?: throw InvalidBackupException("Not a backup archive (no $MANIFEST_ENTRY)")
            parse(readEntryText(zf, entry))
        }
    } catch (e: ZipException) {
        throw InvalidBackupException("Backup file is not a valid zip archive", e)
    }

    /**
     * Reads a text entry (manifest/section) with a hard size cap. This is called on import BEFORE
     * the full integrity pass has run, so it must be safe against a lying/oversized entry on its own.
     */
    private fun readEntryText(zf: ZipFile, entry: ZipEntry): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        zf.getInputStream(entry).use { input ->
            var read = 0L
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                read += n
                if (read > limits.maxTextEntryBytes) {
                    throw InvalidBackupException("Backup entry too large (${entry.name})")
                }
                out.write(buffer, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Parse + version-check raw `manifest.json` content. */
    fun parse(raw: String): BackupEnvelope {
        if (raw.isBlank()) throw InvalidBackupException("Backup manifest was empty")
        val envelope = try {
            json.decodeFromString<BackupEnvelope>(raw)
        } catch (e: SerializationException) {
            throw InvalidBackupException("Not a valid SD Maid backup", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Not a valid SD Maid backup", e)
        }
        if (envelope.version > BackupEnvelope.VERSION) throw UnsupportedBackupVersionException(envelope.version)
        return envelope
    }

    /** The most recent safety snapshot left behind by a failed/interrupted restore, if any. */
    fun findRecoveryBackup(): File? = safetyDir.listFiles().orEmpty()
        .filter { it.name.startsWith(SAFETY_PREFIX) && it.name.endsWith(".zip") }
        .maxByOrNull { it.lastModified() }

    /**
     * Whether [file] is a safety snapshot owned by this manager. Callers must never delete these
     * themselves (they may be the only copy of the pre-restore configuration) — their lifecycle is
     * managed here: deleted after the restore they protect (or the recovery from it) succeeded.
     */
    fun isSafetySnapshot(file: File): Boolean =
        file.name.startsWith(SAFETY_PREFIX) && file.parentFile?.canonicalPath == safetyDir.canonicalPath

    /**
     * Applies a backup zip and returns the restored section keys. Hard-error contract: either every
     * present section applies, or this throws — a partial restore is never reported as success.
     *
     * Order of operations:
     * 1. Archive integrity: per-entry CRC/size verification plus zip-bomb caps ([BackupLimits]).
     * 2. Manifest version gate.
     * 3. Preflight: every present section is decoded ([ConfigBackupContributor.validate]) and every
     *    database validated ([DatabaseBackupContributor.validate]) before anything is written.
     * 4. A complete safety snapshot of the current config is written — all modes, since MERGE also
     *    overwrites same-key values. If it can't be written completely, the restore is refused
     *    ([SafetyBackupFailedException]).
     * 5. Apply. REPLACE aborts on the first failure, MERGE applies everything and then fails if
     *    anything did — both throw [RestoreFailedException] carrying the safety snapshot, which is
     *    kept on failure and deleted on success.
     */
    suspend fun restore(zip: File, mode: RestoreMode): Set<String> = gate.runExclusive {
        log(TAG, INFO) { "restore(mode=$mode): starting" }
        // A safety snapshot exists to bring back the previous configuration verbatim — MERGE would
        // consume it (deleted on success) while leaving the broken state's leftovers in place.
        require(!(mode == RestoreMode.MERGE && isSafetySnapshot(zip))) {
            "Safety snapshots must be restored with REPLACE"
        }
        val zf = try {
            ZipFile(zip)
        } catch (e: ZipException) {
            throw InvalidBackupException("Backup file is not a valid zip archive", e)
        }
        zf.use {
            verifyArchiveIntegrity(zf)

            // Version gate (also throws on a non-backup archive).
            val manifest = zf.getEntry(MANIFEST_ENTRY)
                ?: throw InvalidBackupException("Not a backup archive (no $MANIFEST_ENTRY)")
            parse(zf.getInputStream(manifest).bufferedReader().use { it.readText() })

            // Preflight: decode every present section without applying anything.
            val sections: List<Pair<ConfigBackupContributor, JsonElement>> = sectionContributors
                .sortedBy { it.restoreOrder }
                .mapNotNull { c ->
                    val entry = zf.getEntry("$SECTIONS_DIR${c.key}.json") ?: return@mapNotNull null
                    val element = try {
                        json.parseToJsonElement(readEntryText(zf, entry))
                    } catch (e: Exception) {
                        if (e is InvalidBackupException) throw e
                        throw InvalidBackupException("Section '${c.key}' is not valid JSON", e)
                    }
                    try {
                        c.validate(element)
                    } catch (e: Exception) {
                        throw InvalidBackupException("Section '${c.key}' failed validation", e)
                    }
                    c to element
                }

            // Preflight: extract + validate every present database without applying anything. The
            // list fills as extraction progresses so already-extracted temps are cleaned up even
            // when a later extraction fails.
            val databases = mutableListOf<Pair<DatabaseBackupContributor, File>>()
            try {
                databaseContributors.sortedBy { it.key }.forEach { c ->
                    val entry = zf.getEntry("$DB_DIR${c.key}") ?: return@forEach
                    val tmp = File.createTempFile("restore-${c.key}-", ".db", tmpDir)
                    databases.add(c to tmp)
                    zf.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                }
                databases.forEach { (c, tmp) ->
                    try {
                        c.validate(tmp)
                    } catch (e: Exception) {
                        // Self-describing errors (e.g. schema mismatch) surface as-is; anything raw
                        // (a garbage file that isn't SQLite) becomes a friendly "invalid backup".
                        if (e is HasLocalizedError) throw e
                        throw InvalidBackupException("Database '${c.key}' failed validation", e)
                    }
                }

                // Nothing has been written so far; from here on there is a complete safety net.
                // Restoring FROM a safety snapshot (the recovery/undo path) doesn't snapshot again:
                // the source itself is the net — snapshotting would only capture the broken state
                // the user is recovering from.
                val safety = if (isSafetySnapshot(zip)) zip else writeSafetyBackup(restoreSource = zip)

                val restored = mutableSetOf<String>()
                val failures = mutableListOf<SectionFailure>()
                fun failed(key: String, e: Exception) {
                    log(TAG, ERROR) { "restore() failed for '$key': ${e.asLog()}" }
                    failures.add(SectionFailure(key, e))
                    // REPLACE deletes as it applies — abort on the first failure instead of digging
                    // the hole deeper. MERGE is additive, so the rest is still applied and the
                    // combined failure is thrown at the end.
                    if (mode == RestoreMode.REPLACE) {
                        throw RestoreFailedException(failures.map { it.key }, e, safety)
                    }
                }

                sections.forEach { (c, element) ->
                    try {
                        c.restore(element, mode)
                        restored += c.key
                    } catch (e: Exception) {
                        failed(c.key, e)
                    }
                }
                databases.forEach { (c, tmp) ->
                    try {
                        c.restoreFrom(tmp, mode)
                        restored += c.key
                    } catch (e: Exception) {
                        failed(c.key, e)
                    }
                }
                if (failures.isNotEmpty()) {
                    throw RestoreFailedException(failures.map { it.key }, failures.first().error, safety)
                }

                safety.delete()
                log(TAG, INFO) { "restore(mode=$mode): restored ${restored.size} ${restored.sorted()}" }
                restored
            } finally {
                databases.forEach { it.second.delete() }
            }
        }
    }

    /**
     * Snapshots the CURRENT configuration to [safetyDir] before a restore mutates it. The snapshot
     * must be complete — a partial safety net is no net, so any failure refuses the restore
     * ([SafetyBackupFailedException]). Older snapshots are only purged AFTER the new one is fully
     * written, so a failing snapshot write can never destroy an existing recovery file; unique
     * names + sparing [restoreSource] make sure re-restoring a kept snapshot can't self-overwrite.
     */
    private suspend fun writeSafetyBackup(restoreSource: File): File {
        val dir = safetyDir
        val sourceCanonical = restoreSource.canonicalPath
        val target = File(dir, "$SAFETY_PREFIX${System.currentTimeMillis()}.zip")
        check(target.canonicalPath != sourceCanonical) { "Safety snapshot would overwrite the restore source" }
        val tmp = File(dir, "${target.name}.tmp")
        try {
            val result = tmp.outputStream().use { writeArchive(it) }
            if (!result.isCompleteSuccess) {
                throw SafetyBackupFailedException(failedSections = result.failures.map { it.key })
            }
            if (!tmp.renameTo(target)) throw IOException("Could not finalize $target")
        } catch (e: Exception) {
            tmp.delete()
            throw if (e is SafetyBackupFailedException) e else SafetyBackupFailedException(cause = e)
        }
        dir.listFiles().orEmpty()
            .filter { it.name.startsWith(SAFETY_PREFIX) }
            .filter { it.canonicalPath != target.canonicalPath && it.canonicalPath != sourceCanonical }
            .forEach { stale ->
                if (stale.delete()) log(TAG) { "writeSafetyBackup(): purged stale snapshot $stale" }
            }
        log(TAG, INFO) { "writeSafetyBackup(): current config snapshotted to $target" }
        return target
    }

    /**
     * Reads + checksums every entry before any data is applied, so a corrupt or truncated archive
     * surfaces as [InvalidBackupException] up front rather than failing mid-restore. We compute the
     * CRC32 ourselves (and compare the byte count) instead of trusting the stream to validate it —
     * `ZipFile.getInputStream()` does not reliably verify CRC for all entry types/platforms.
     * [BackupLimits] are enforced mid-stream so a zip bomb fails fast, not after inflating.
     */
    private fun verifyArchiveIntegrity(zf: ZipFile) {
        if (zf.size() > limits.maxEntries) {
            throw InvalidBackupException("Backup archive has too many entries (${zf.size()})")
        }
        val buffer = ByteArray(16 * 1024)
        val entries = zf.entries()
        var total = 0L
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            // Text entries (manifest/sections) are read into memory whole later, so they get the
            // much tighter cap; only database files may be large.
            val entryCap = if (entry.name.startsWith(DB_DIR)) limits.maxEntryBytes else limits.maxTextEntryBytes
            val crc = CRC32()
            var read = 0L
            try {
                zf.getInputStream(entry).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        crc.update(buffer, 0, n)
                        read += n
                        total += n
                        if (read > entryCap) {
                            throw InvalidBackupException("Backup entry too large (${entry.name})")
                        }
                        if (total > limits.maxTotalBytes) {
                            throw InvalidBackupException("Backup archive too large when unpacked")
                        }
                    }
                }
            } catch (e: IOException) {
                throw InvalidBackupException("Backup archive is corrupt (${entry.name})", e)
            }
            if (entry.crc != -1L && crc.value != entry.crc) {
                throw InvalidBackupException("Backup archive is corrupt (${entry.name}: CRC mismatch)")
            }
            if (entry.size != -1L && read != entry.size) {
                throw InvalidBackupException("Backup archive is corrupt (${entry.name}: size mismatch)")
            }
        }
    }

    data class WriteResult(
        val written: Set<String>,
        val failures: List<SectionFailure>,
    ) {
        val isCompleteSuccess: Boolean get() = failures.isEmpty()
    }

    data class SectionFailure(
        val key: String,
        val error: Throwable,
    )

    companion object {
        private val TAG = logTag("Backup", "Manager")
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val SECTIONS_DIR = "sections/"
        private const val DB_DIR = "databases/"
        private const val SAFETY_PREFIX = "pre-restore-"
    }
}
