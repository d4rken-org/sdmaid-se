package eu.darken.sdmse.appcleaner.core.automation.specs

import android.app.usage.StorageStats
import android.content.Context
import android.graphics.Rect
import android.text.format.Formatter
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.findParentOrNull
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.isTextView
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.common.textVariants
import eu.darken.sdmse.automation.core.common.toStringShort
import eu.darken.sdmse.automation.core.waitForWindowRoot
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
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
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.pow

class StorageEntryFinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsManager: StorageStatsManager2,
) {
    internal suspend fun createSizeMatcher(pkg: Installed): ((AccessibilityNodeInfo) -> Boolean)? {
        log(TAG) { "createSizeMatcher(${pkg.installId} initialising..." }

        val targetSize = determineTargetSize(pkg) ?: return null
        val targetTexts = generateTargetTexts(targetSize)

        log(TAG) {
            val distinguisable = targetTexts.map { it.replace("\u00A0", "\\u00A0") }
            "Loaded ${targetTexts.size} targets from $targetSize for ${pkg.installId}: $distinguisable"
        }

        return { node ->
            // target needs to be either at index 0 or be preceeded by a space
            // OK: "80 MB lorem ipsum", "lorem 80 MB ipsum"
            // NOT OKAY: "1.80 MB lorem ipsum", "lorem 1.80 MB ipsum"
            node.textVariants.any outer@{ candidate ->
                targetTexts.any inner@{ target ->
                    val index = candidate.indexOf(target)
                    if (index == -1) return@inner false
                    index == 0 || candidate.getOrNull(index - 1)?.isWhitespace() == true
                }
            }.also {
                if (Bugs.isDebug) {
                    if (it) log(TAG) { "Matched for $targetTexts on ${node.textVariants} from ${pkg.installId}" }
                    else log(TAG, VERBOSE) { "Miss for $targetTexts on ${node.textVariants} from ${pkg.installId}" }
                }
            }
        }
    }

    internal suspend fun determineTargetSize(pkg: Installed): Long? {
        if (!Permission.PACKAGE_USAGE_STATS.isGranted(context)) {
            log(TAG) { "determineTargetSize(...): Missing PACKAGE_USAGE_STATS" }
            return null
        }

        val storageId = pkg.applicationInfo?.storageUuid?.let { StorageId(internalId = null, externalId = it) }
        if (storageId == null) {
            log(TAG, WARN) { "Couldn't determine StorageId via appInfo=${pkg.applicationInfo}" }
            return null
        }

        val stats1: StorageStats = try {
            // OS uses queryStatsForPkg and NOT queryStatsForAppUid
            // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/SettingsLib/SpaPrivileged/src/com/android/settingslib/spaprivileged/template/app/AppStorageSize.kt;l=51
            statsManager.queryStatsForPkg(storageId, pkg)
        } catch (e: SecurityException) {
            log(TAG, WARN) { "Don't have permission to query app size for ${pkg.id}: $e" }
            return null
        } catch (e: Exception) {
            log(TAG, ERROR) { "Unexpected error when querying app size for ${pkg.id}: ${e.asLog()}" }
            return null
        }

        return stats1.appBytes + stats1.dataBytes
    }

    internal fun generateTargetTexts(targetSize: Long): Set<String> {
        val targetTexts = mutableSetOf<String>()

        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/SettingsLib/SpaPrivileged/src/com/android/settingslib/spaprivileged/template/app/AppStorageSize.kt
        Formatter.formatFileSize(context, targetSize).run {
            log(TAG, VERBOSE) { "formatFileSize=$this" }
            targetTexts.add(this)
        }

        Formatter.formatShortFileSize(context, targetSize).run {
            log(TAG, VERBOSE) { "formatShortFileSize=$this" }
            targetTexts.add(this)
        }

        try {
            formatExtraFileSize(targetSize).run {
                log(TAG, VERBOSE) { "formatExtraFileSize=$this" }
                targetTexts.add(this)
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "formatExtraFileSize($targetSize) failed: ${e.asLog()}" }
        }

        targetTexts.mapNotNull {
            when {
                it.contains(".") -> setOf(it.replace(".", ","))
                it.contains(",") -> setOf(it.replace(",", "."))
                else -> null
            }
        }.forEach { targetTexts.addAll(it) }

        targetTexts.mapNotNull {
            when {
                it.contains(" ") -> setOf(it.replace(" ", " "))
                it.contains(" ") -> setOf(it.replace(" ", " "))
                else -> null
            }
        }.forEach { targetTexts.addAll(it) }

        return targetTexts
    }

    suspend fun storageFinderAOSP(
        labels: Collection<String>,
        pkg: Installed,
    ): suspend StepContext.() -> AccessibilityNodeInfo? = {
        val matchStorage = createSizeMatcher(pkg) ?: { false }

        val storageFilter: (AccessibilityNodeInfo) -> Boolean = when {
            hasApiLevel(33) -> storageFilter@{ node ->
                if (!node.isTextView()) return@storageFilter false
                node.textMatchesAny(labels) || matchStorage(node)
            }

            else -> storageFilter@{ node ->
                if (!node.isTextView()) return@storageFilter false
                if (node.idContains("android:id/title")) {
                    node.textMatchesAny(labels)
                } else if (node.idContains("android:id/summary")) {
                    matchStorage(node)
                } else {
                    false
                }
            }
        }

        val matches = host.waitForWindowRoot().crawl()
            .map { it.node }
            .filter { storageFilter(it) }
            .toMutableList()

        log(TAG, if (matches.size > 1) WARN else DEBUG) { "Got ${matches.size} matches" }
        matches.forEachIndexed { index, nodeInfo -> log(TAG) { "#$index - ${nodeInfo.toStringShort()}" } }

        // Siblings in storage entry
        if (matches.size == 2) {
            val first = matches[0]
            val second = matches[1]
            if (first.parent == second.parent) {
                log(TAG, WARN) { "Double match on entry summary, removing summary: ${second.toStringShort()}" }
                matches.remove(second)
            }
        }

        // In some multipane layouts the "Storage" text can appear in the left menu pane
        // If we find out that our matched node has the wrong parent, then it's a false positive
        matches.removeIf { node ->
            if (matches.size < 2) return@removeIf false
            val remove = node.findParentOrNull(maxNesting = 11) { parent ->
                setOf(
                    // crawl():------6: className=android.widget.LinearLayout, text='null', isClickable=false, isEnabled=true, viewIdResourceName=com.android.settings:id/ll_landleft, pkgName=com.android.settings, identity=75f034b
                    // crawl():----------------16: className=android.widget.TextView, text='Speicher', isClickable=false, isEnabled=true, viewIdResourceName=android:id/title, pkgName=com.android.settings, identity=8aa3292
                    // On a Lenovo M10 Plus we identify the panes
                    // Left pane (main menu) has parent: com.android.settings:id/ll_landleft
                    // Right pane (app setting details) has parent: com.android.settings:id/ll_landright
                    "ll_landleft",
                    // See https://github.com/d4rken-org/sdmaid-se/issues/1720
                    "left_fragment"
                ).any { parent.idContains(it) }
            } != null
            if (remove) log(TAG, WARN) { "Removed false-positive left pane entry by ID: $node" }
            remove
        }

        matches.removeIf { node ->
            if (matches.size < 2) return@removeIf false
            log(TAG) { "PanePosition: Checking $node" }
            val gp = node.parent?.parent
            log(TAG) { "PanePosition: Grand-Parent is $gp" }
            val remove = gp?.let { determinePane(it) == ACSNodePaneState.LEFT } ?: false
            if (remove) log(TAG, WARN) { "Removed false-positive left pane by position: $node" }
            remove
        }

        matches.firstOrNull()
    }

    enum class ACSNodePaneState {
        LEFT, RIGHT, FULL
    }

    private fun StepContext.determinePane(node: AccessibilityNodeInfo, margin: Int = 50): ACSNodePaneState {
        val metrics = host.service.resources.displayMetrics
        val screenMidX = metrics.widthPixels / 2

        val nodeBounds = Rect().apply { node.getBoundsInScreen(this) }

        return when {
            nodeBounds.right < screenMidX - margin -> ACSNodePaneState.LEFT
            nodeBounds.left > screenMidX + margin -> ACSNodePaneState.RIGHT
            else -> ACSNodePaneState.FULL
        }
    }

    private fun formatExtraFileSize(bytes: Long): String {
        if (bytes < 1000) return "$bytes B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
        val exp = (ln(bytes.toDouble()) / ln(1000.0)).toInt()
        val value = bytes / 1000.0.pow(exp.toDouble())
        return String.format(Locale.getDefault(), "%.2f %s", value, units[exp])
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "StorageEntryFinder")
    }
}