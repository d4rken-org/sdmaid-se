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
 * Reads/writes a backup archive (a zip): `config.json` holds the [BackupEnvelope] (metadata + the
 * settings/content sections), and each database is a separate `databases/<key>` entry copied at the
 * SQLite-file level so memory stays flat regardless of row count.
 *
 * Export is Pro-gated; import/restore is free. Per-section/per-db failures are isolated.
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

        val sections = sectionContributors
            .sortedBy { it.key }
            .mapNotNull { c ->
                try {
                    c.snapshot()?.let { c.key to it }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "snapshot() failed for '${c.key}': ${e.asLog()}" }
                    null
                }
            }
            .toMap()
        val envelope = newEnvelope(sections)

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(CONFIG_ENTRY))
            zip.write(json.encodeToString(envelope).toByteArray())
            zip.closeEntry()

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
        log(TAG, INFO) { "writeBackup(): ${sections.size} sections + ${databaseContributors.size} databases" }
    }

    /** Parse + version-check `config.json` from a backup zip, without applying it. */
    fun parse(zip: File): BackupEnvelope = ZipFile(zip).use { zf ->
        val entry = zf.getEntry(CONFIG_ENTRY) ?: throw InvalidBackupException("Not a backup archive (no $CONFIG_ENTRY)")
        val raw = zf.getInputStream(entry).bufferedReader().use { it.readText() }
        parse(raw)
    }

    /** Parse + version-check raw `config.json` content. */
    fun parse(raw: String): BackupEnvelope {
        if (raw.isBlank()) throw InvalidBackupException("Backup config was empty")
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

    /** Applies a backup zip: JSON sections first (content before settings), then databases. */
    suspend fun restore(zip: File, mode: RestoreMode): RestoreResult {
        val failures = mutableListOf<SectionFailure>()
        val restored = mutableSetOf<String>()

        ZipFile(zip).use { zf ->
            val envelope = parse(zip)
            log(TAG, INFO) { "restore(mode=$mode): sections=${envelope.sections.keys}" }

            sectionContributors.sortedBy { it.restoreOrder }.forEach { c ->
                val data = envelope.sections[c.key] ?: return@forEach
                try {
                    c.restore(data, mode)
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

    private fun newEnvelope(sections: Map<String, kotlinx.serialization.json.JsonElement>) = BackupEnvelope(
        createdAt = Instant.now(),
        appVersionCode = BuildConfigWrap.VERSION_CODE,
        appVersionName = BuildConfigWrap.VERSION_NAME,
        flavor = BuildConfigWrap.FLAVOR.name,
        androidSdkInt = Build.VERSION.SDK_INT,
        androidRelease = Build.VERSION.RELEASE ?: "?",
        deviceManufacturer = Build.MANUFACTURER ?: "?",
        deviceModel = Build.MODEL ?: "?",
        sections = sections,
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
        private const val CONFIG_ENTRY = "config.json"
        private const val DB_DIR = "databases/"
    }
}
