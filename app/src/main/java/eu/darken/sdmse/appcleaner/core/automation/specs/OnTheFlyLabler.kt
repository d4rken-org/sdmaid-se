package eu.darken.sdmse.appcleaner.core.automation.specs

import android.app.usage.StorageStats
import android.content.Context
import android.text.format.Formatter
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.isTextView
import eu.darken.sdmse.automation.core.common.textContainsAny
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.common.textVariants
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageStatsManager2
import javax.inject.Inject

class OnTheFlyLabler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsManager: StorageStatsManager2,
) {
    suspend fun isStorageEntry(pkg: Installed, node: AccessibilityNodeInfo): Boolean {
        if (!Permission.PACKAGE_USAGE_STATS.isGranted(context)) {
            log(TAG) { "isStorageEntry(...): Missing PACKAGE_USAGE_STATS" }
            return false
        }

        val storageId = pkg.applicationInfo?.storageUuid?.let { StorageId(internalId = null, externalId = it) }
        if (storageId == null) {
            log(TAG, WARN) { "Couldn't determine StorageId via appInfo=${pkg.applicationInfo}" }
            return false
        }

        val stats1: StorageStats = try {
            // OS uses queryStatsForPkg and NOT queryStatsForAppUid
            // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/SettingsLib/SpaPrivileged/src/com/android/settingslib/spaprivileged/template/app/AppStorageSize.kt;l=51
            statsManager.queryStatsForPkg(storageId, pkg)
        } catch (e: SecurityException) {
            log(TAG, WARN) { "Don't have permission to query app size for ${pkg.id}: $e" }
            return false
        } catch (e: Exception) {
            log(TAG, ERROR) { "Unexpected error when querying app size for ${pkg.id}: ${e.asLog()}" }
            return false
        }

        val targetSize = stats1.appBytes + stats1.dataBytes

        val targetTexts = setOf(
            // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/SettingsLib/SpaPrivileged/src/com/android/settingslib/spaprivileged/template/app/AppStorageSize.kt
            Formatter.formatFileSize(context, targetSize),
            Formatter.formatShortFileSize(context, targetSize),
        )

        return node.textContainsAny(targetTexts).also {
            if (Bugs.isDebug) {
                if (it) log(TAG) { "Matched for $targetTexts on ${node.textVariants} from ${pkg.installId}" }
                else log(TAG, VERBOSE) { "Miss for $targetTexts on ${node.textVariants} from ${pkg.installId}" }
            }

        }
    }

    fun getAOSPStorageFilter(
        labels: Collection<String>,
        pkg: Installed,
    ): suspend (AccessibilityNodeInfo) -> Boolean = when {
        hasApiLevel(33) -> storageFilter@{ node ->
            if (!node.isTextView()) return@storageFilter false
            node.textMatchesAny(labels) || isStorageEntry(pkg, node)
        }

        else -> storageFilter@{ node ->
            if (!node.isTextView()) return@storageFilter false

            if (node.idContains("android:id/title")) {
                node.textMatchesAny(labels)
            } else if (node.idContains("android:id/summary")) {
                isStorageEntry(pkg, node)
            } else {
                false
            }
        }
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnTheFlyLabler")
    }
}