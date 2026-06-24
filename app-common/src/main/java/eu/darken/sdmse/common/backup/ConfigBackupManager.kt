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
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
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

    /** Streams a full backup zip into [out]. Throws [UpgradeRequiredException] for non-Pro users. */
    suspend fun writeBackup(out: OutputStream) {
        if (!upgradeRepo.isProSettled()) {
            log(TAG, WARN) { "writeBackup() denied, Pro upgrade required" }
            throw UpgradeRequiredException()
        }

        var sectionCount = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(newEnvelope()).toByteArray())
            zip.closeEntry()

            sectionContributors.sortedBy { it.key }.forEach { c ->
                val element = try {
                    c.snapshot()
                } catch (e: Exception) {
                    log(TAG, ERROR) { "snapshot() failed for '${c.key}': ${e.asLog()}" }
                    null
                } ?: return@forEach
                zip.putNextEntry(ZipEntry("$SECTIONS_DIR${c.key}.json"))
                zip.write(json.encodeToString(element).toByteArray())
                zip.closeEntry()
                sectionCount++
            }

            databaseContributors.sortedBy { it.key }.forEach { c ->
                val tmp = File.createTempFile("export-${c.key}-", ".db", tmpDir)
                try {
                    c.exportTo(tmp)
                    if (tmp.length() > 0) {
                        zip.putNextEntry(ZipEntry("$DB_DIR${c.key}"))
                        tmp.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "exportTo() failed for '${c.key}': ${e.asLog()}" }
                } finally {
                    tmp.delete()
                }
            }
        }
        log(TAG, INFO) { "writeBackup(): $sectionCount sections + ${databaseContributors.size} databases" }
    }

    /** Parse + version-check `manifest.json` from a backup zip, without applying it. */
    fun parse(zip: File): BackupEnvelope = ZipFile(zip).use { zf ->
        val entry = zf.getEntry(MANIFEST_ENTRY)
            ?: throw InvalidBackupException("Not a backup archive (no $MANIFEST_ENTRY)")
        parse(zf.getInputStream(entry).bufferedReader().use { it.readText() })
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
        val failures = mutableListOf<SectionFailure>()
        val restored = mutableSetOf<String>()

        ZipFile(zip).use { zf ->
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
        return RestoreResult(restored = restored, failures = failures)
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
