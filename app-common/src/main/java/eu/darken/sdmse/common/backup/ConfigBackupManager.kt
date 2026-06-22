package eu.darken.sdmse.common.backup

import android.os.Build
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
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates all [ConfigBackupContributor]s into a single backup file and restores from one.
 *
 * Export is Pro-gated as a backend safety net (the UI gates too). Import/restore is free.
 * Per-contributor failures are isolated: one bad section never aborts the rest — it is collected
 * into the [RestoreResult] (restore) or skipped with a warning (export).
 */
@Singleton
class ConfigBackupManager @Inject constructor(
    private val contributors: Set<@JvmSuppressWildcards ConfigBackupContributor>,
    private val json: Json,
    private val upgradeRepo: UpgradeRepo,
) {

    /** Builds the serialized backup. Throws [UpgradeRequiredException] for non-Pro users. */
    suspend fun createBackup(): String {
        if (!upgradeRepo.isProSettled()) {
            log(TAG, WARN) { "createBackup() denied, Pro upgrade required" }
            throw UpgradeRequiredException()
        }

        val sections = contributors
            .sortedBy { it.key }
            .mapNotNull { contributor ->
                try {
                    contributor.snapshot()?.let { contributor.key to it }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "snapshot() failed for '${contributor.key}': ${e.asLog()}" }
                    null
                }
            }
            .toMap()

        val envelope = BackupEnvelope(
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
        log(TAG, INFO) { "createBackup(): ${sections.size} sections -> ${sections.keys}" }
        return json.encodeToString(envelope)
    }

    /** Parses + version-checks a backup without applying it, so the UI can warn before restoring. */
    fun parse(raw: String): BackupEnvelope {
        if (raw.isBlank()) throw InvalidBackupException("Backup file was empty")
        val envelope = try {
            json.decodeFromString<BackupEnvelope>(raw)
        } catch (e: SerializationException) {
            throw InvalidBackupException("Not a valid SD Maid backup", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Not a valid SD Maid backup", e)
        }
        if (envelope.version > BackupEnvelope.VERSION) {
            throw UnsupportedBackupVersionException(envelope.version)
        }
        return envelope
    }

    /** Applies a parsed [envelope]; missing/unknown sections are skipped. */
    suspend fun restore(envelope: BackupEnvelope, mode: RestoreMode): RestoreResult {
        log(TAG, INFO) { "restore(mode=$mode): sections=${envelope.sections.keys}" }
        val failures = mutableListOf<SectionFailure>()
        contributors
            .sortedBy { it.restoreOrder }
            .forEach { contributor ->
                val data = envelope.sections[contributor.key] ?: return@forEach
                try {
                    contributor.restore(data, mode)
                    log(TAG) { "restore(): '${contributor.key}' applied" }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "restore() failed for '${contributor.key}': ${e.asLog()}" }
                    failures.add(SectionFailure(contributor.key, e))
                }
            }
        return RestoreResult(restored = envelope.sections.keys, failures = failures)
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
    }
}
