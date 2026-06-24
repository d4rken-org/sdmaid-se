package eu.darken.sdmse.common.backup

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.UpgradeRequiredException
import eu.darken.sdmse.common.upgrade.isProSettled
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
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
 * Export is Pro-gated; import/restore is free. Per-entry failures are isolated.
 */
@Singleton
class ConfigBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sectionContributors: Set<@JvmSuppressWildcards ConfigBackupContributor>,
    private val databaseContributors: Set<@JvmSuppressWildcards DatabaseBackupContributor>,
    private val json: Json,
    private val upgradeRepo: UpgradeRepo,
) {

    private val tmpDir: File
        get() = File(context.cacheDir, "backup").apply { mkdirs() }

    // Backup entries are written pretty-printed so the archive is human-inspectable. Derived from the
    // injected json so it keeps the app's serializers module; parsing on restore stays on `json`.
    private val prettyJson = Json(from = json) { prettyPrint = true }

    /**
     * Streams a full backup zip into [out]. Throws [UpgradeRequiredException] for non-Pro users.
     *
     * Per-contributor failures are isolated (the rest of the archive is still written) and reported in
     * the returned [WriteResult] so callers can surface a partial backup instead of a false success.
     */
    suspend fun writeBackup(out: OutputStream): WriteResult {
        if (!upgradeRepo.isProSettled()) {
            log(TAG, WARN) { "writeBackup() denied, Pro upgrade required" }
            throw UpgradeRequiredException()
        }
        log(TAG, INFO) { "writeBackup(): starting" }

        val result = ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(prettyJson.encodeToString(newEnvelope()).toByteArray())
            zip.closeEntry()
            writeEntries(zip)
        }
        log(TAG, INFO) { "writeBackup(): wrote ${result.written.size} entries ${result.written.sorted()}, ${result.failures.size} failures" }
        return result
    }

    /**
     * Writes every contributor's section + database entry into an already-open [zip] and returns the
     * per-entry outcome. Split out from [writeBackup] so it can be unit-tested without the manifest
     * write (which reads [BuildConfigWrap], unavailable in app-common JVM tests).
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
            parse(zf.getInputStream(entry).bufferedReader().use { it.readText() })
        }
    } catch (e: ZipException) {
        throw InvalidBackupException("Backup file is not a valid zip archive", e)
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

    /** Applies a backup zip: section files first (content before settings), then databases. */
    suspend fun restore(zip: File, mode: RestoreMode): RestoreResult {
        log(TAG, INFO) { "restore(mode=$mode): starting" }
        val failures = mutableListOf<SectionFailure>()
        val restored = mutableSetOf<String>()

        val zf = try {
            ZipFile(zip)
        } catch (e: ZipException) {
            throw InvalidBackupException("Backup file is not a valid zip archive", e)
        }
        zf.use {
            // Integrity pre-pass: read every entry (incl. the manifest) so a corrupt/truncated archive
            // fails before we apply anything (REPLACE wipes as it goes, so a mid-restore failure would
            // otherwise leave a partially-restored state).
            verifyArchiveIntegrity(zf)

            // Version gate (also throws on a non-backup archive).
            val manifest = zf.getEntry(MANIFEST_ENTRY)
                ?: throw InvalidBackupException("Not a backup archive (no $MANIFEST_ENTRY)")
            parse(zf.getInputStream(manifest).bufferedReader().use { it.readText() })

            sectionContributors.sortedBy { it.restoreOrder }.forEach { c ->
                val entry = zf.getEntry("$SECTIONS_DIR${c.key}.json") ?: return@forEach
                try {
                    val element = json.parseToJsonElement(
                        zf.getInputStream(entry).bufferedReader().use { it.readText() },
                    )
                    c.restore(element, mode)
                    restored += c.key
                } catch (e: Exception) {
                    log(TAG, ERROR) { "restore() failed for '${c.key}': ${e.asLog()}" }
                    failures.add(SectionFailure(c.key, e))
                }
            }

            databaseContributors.sortedBy { it.key }.forEach { c ->
                val entry = zf.getEntry("$DB_DIR${c.key}") ?: return@forEach
                val tmp = File.createTempFile("restore-${c.key}-", ".db", tmpDir)
                try {
                    zf.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    c.restoreFrom(tmp, mode)
                    restored += c.key
                } catch (e: Exception) {
                    log(TAG, ERROR) { "restoreFrom() failed for '${c.key}': ${e.asLog()}" }
                    failures.add(SectionFailure(c.key, e))
                } finally {
                    tmp.delete()
                }
            }
        }
        log(TAG, INFO) { "restore(mode=$mode): restored ${restored.size} ${restored.sorted()}, ${failures.size} failures" }
        return RestoreResult(restored = restored, failures = failures)
    }

    /**
     * Reads + checksums every entry before any data is applied, so a corrupt or truncated archive
     * surfaces as [InvalidBackupException] up front rather than failing mid-restore. We compute the
     * CRC32 ourselves (and compare the byte count) instead of trusting the stream to validate it —
     * `ZipFile.getInputStream()` does not reliably verify CRC for all entry types/platforms.
     */
    private fun verifyArchiveIntegrity(zf: ZipFile) {
        val buffer = ByteArray(16 * 1024)
        val entries = zf.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val crc = CRC32()
            var read = 0L
            try {
                zf.getInputStream(entry).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        crc.update(buffer, 0, n)
                        read += n
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

    private fun newEnvelope() = BackupEnvelope(
        createdAt = Instant.now(),
        appVersionCode = BuildConfigWrap.VERSION_CODE,
        appVersionName = BuildConfigWrap.VERSION_NAME,
        flavor = BuildConfigWrap.FLAVOR.name,
        androidSdkInt = Build.VERSION.SDK_INT,
        androidRelease = Build.VERSION.RELEASE ?: "?",
        deviceManufacturer = Build.MANUFACTURER ?: "?",
        deviceModel = Build.MODEL ?: "?",
    )

    data class WriteResult(
        val written: Set<String>,
        val failures: List<SectionFailure>,
    ) {
        val isCompleteSuccess: Boolean get() = failures.isEmpty()
    }

    data class RestoreResult(
        val restored: Set<String>,
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
    }
}
