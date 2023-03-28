package eu.darken.sdmse.appcleaner.core.automation.specs

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationStepGenerator
import eu.darken.sdmse.automation.core.AutomationStepGenerator.Companion.getSysLocale
import eu.darken.sdmse.automation.core.crawler.*
import eu.darken.sdmse.automation.core.pkgId
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject


@Reusable
class MIUI12Specs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val pkgRepo: PkgRepo,
) : MIUI11Specs(ipcFunnel, context, deviceDetective, pkgRepo) {

    override val label = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (!hasApiLevel(28) || !deviceDetective.isXiaomi()) return false
        if (VERSION_STARTS.none { Build.VERSION.INCREMENTAL.startsWith(it) }) return false
        return pkgRepo.isInstalled(SETTINGS_PKG)
    }

    override suspend fun getSpecs(pkg: Installed): List<AutomationCrawler.Step> {
        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script
        val country = locale.country
        log(TAG, VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        val steps = mutableListOf<AutomationCrawler.Step>()

        val clearDataLabels = getClearDataButtonLabels(lang, script, country)
        val clearCacheLabels = getClearCacheButtonLabels(lang, script, country)
        val dialogTitles = getDialogTitles(lang, script, country)

        run {
            val clearDataFilter: suspend (AccessibilityNodeInfo) -> Boolean = filter@{ node ->
                if (!node.isTextView()) return@filter false

                if (node.textMatchesAny(clearDataLabels)) return@filter true

                if (node.textMatchesAny(clearCacheLabels)) {
                    val altStep = AutomationCrawler.Step(
                        parentTag = TAG,
                        pkgInfo = pkg,
                        label = "BRANCH: Find & Click 'Clear cache' (targets=$clearCacheLabels)",
                        windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                        nodeTest = { it.isTextView() && it.textMatchesAny(clearCacheLabels) },
                        nodeMapping = CrawlerCommon.clickableParent(),
                        action = CrawlerCommon.defaultClick()
                    )
                    throw BranchException(
                        "Got 'Clear cache' instead of 'Clear data' skip the action dialog step.",
                        listOf(altStep),
                        invalidSteps = 1
                    )
                }
                return@filter false
            }
            // MIUI 12 needs a node mapping, while in MIUI 11 the text is directly clickable.
            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click MIUI 'Clear data' (targets=$clearDataLabels)",
                    windowIntent = CrawlerCommon.defaultWindowIntent(context, pkg),
                    windowEventFilter = { event ->
                        // Some MIUI14 devices send the change event for the system settings app
                        event.pkgId == SETTINGS_PKG || event.pkgId == "com.android.settings".toPkgId()
                    },
                    windowNodeTest = CrawlerCommon.windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                    nodeTest = clearDataFilter,
                    nodeRecovery = CrawlerCommon.getDefaultNodeRecovery(pkg),
                    nodeMapping = CrawlerCommon.clickableParent(),
                    action = CrawlerCommon.defaultClick()
                )
            )
        }

        // Clear data
        // -> Clear data
        // -> Clear cache
        // -> Cancel
        // This may be skipped when MIUI just shows a 'Clear cache' option
        run {
            val windowCriteria = fun(node: AccessibilityNodeInfo): Boolean {
                if (node.pkgId != SETTINGS_PKG) return false
                return node.crawl().map { it.node }.any { it.idContains("id/alertTitle") }
            }
            val entryFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickable || !node.isTextView()) return false
                return node.textMatchesAny(clearCacheLabels)
            }

            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click 'Clear Cache' entry in bottom sheet (targets=$clearCacheLabels)",
                    windowNodeTest = windowCriteria,
                    nodeTest = entryFilter,
                    action = CrawlerCommon.defaultClick()
                )
            )
        }

        // Clear cache?
        // -> Cancel        -> Ok
        run {
            val windowCriteria = fun(node: AccessibilityNodeInfo): Boolean {
                if (node.pkgId != SETTINGS_PKG) return false
                return node.crawl().map { it.node }.any { subNode ->
                    if (!subNode.idContains("id/alertTitle")) return@any false
                    return@any when {
                        subNode.textMatchesAny(dialogTitles) -> true
                        subNode.textMatchesAny(dialogTitles.map { it.replace("?", "") }) -> true
                        subNode.textMatchesAny(dialogTitles.map { "$it?" }) -> true
                        subNode.textEndsWithAny(clearCacheLabels.map { "$it?" }) -> true
                        subNode.textEndsWithAny(clearCacheLabels) -> true
                        else -> false
                    }
                }
            }

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickyButton()) return false
                return when (Bugs.isDryRun) {
                    true -> node.idMatches("android:id/button2")
                    false -> node.idMatches("android:id/button1")
                }
            }

            steps.add(
                AutomationCrawler.Step(
                    parentTag = TAG,
                    pkgInfo = pkg,
                    label = "Find & click 'OK' in confirmation dialog",
                    windowNodeTest = windowCriteria,
                    nodeTest = buttonFilter,
                    action = CrawlerCommon.defaultClick()
                )
            )
        }

        return steps
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: MIUI12Specs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "MIUI12Specs")
        private val SETTINGS_PKG = "com.miui.securitycenter".toPkgId()
        private val VERSION_STARTS = arrayOf(
            // Xiaomi/raphael_eea/raphael:10/QKQ1.190825.002/V12.0.1.0.QFKEUXM:user/release-keys
            "V12",
            // Xiaomi/venus_eea/venus:12/SKQ1.211006.001/V13.0.1.0.SKBEUXM:user/release-keys
            "V13",
            // Xiaomi/plato_id/plato:13/TP1A.220624.014/V14.0.1.0.TLQIDXM:user/release-keys
            "V14",
        )
    }

}