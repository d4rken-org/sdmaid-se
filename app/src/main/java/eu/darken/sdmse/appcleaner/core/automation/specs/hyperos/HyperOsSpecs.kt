package eu.darken.sdmse.appcleaner.core.automation.specs.hyperos

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.pm.PackageInfoCompat
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.OnTheFlyLabler
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.common.StepProcessor
import eu.darken.sdmse.automation.core.common.clickableParent
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.defaultClick
import eu.darken.sdmse.automation.core.common.defaultWindowIntent
import eu.darken.sdmse.automation.core.common.gestureClick
import eu.darken.sdmse.automation.core.common.getAospClearCacheClick
import eu.darken.sdmse.automation.core.common.getDefaultNodeRecovery
import eu.darken.sdmse.automation.core.common.getSysLocale
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.idMatches
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.isRadioButton
import eu.darken.sdmse.automation.core.common.isTextView
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.textEndsWithAny
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.common.waitUntilSettled
import eu.darken.sdmse.automation.core.common.windowCriteriaAppIdentifier
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.deviceadmin.DeviceAdminManager
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getPackageInfo2
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject


@Reusable
class HyperOsSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val hyperOsLabels: HyperOsLabels,
    private val aospLabels: AOSPLabels,
    private val deviceAdminManager: DeviceAdminManager,
    private val onTheFlyLabler: OnTheFlyLabler,
    private val generalSettings: GeneralSettings,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.HYPEROS) return true
        if (romType != RomType.AUTO) return false

        return deviceDetective.getROMType() == RomType.HYPEROS
    }

    override suspend fun getClearCache(pkg: Installed): AutomationSpec = object : AutomationSpec.Explorer {
        override val tag: String = TAG
        override suspend fun createPlan(): suspend AutomationExplorer.Context.() -> Unit = {
            mainPlan(pkg)
        }
    }

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = { pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        var windowPkg: Pkg.Id? = null

        val step = StepProcessor.Step(
            source = TAG,
            descriptionInternal = "Storage entry (main plan)",
            label = R.string.appcleaner_automation_progress_find_storage.toCaString(""),
            windowIntent = defaultWindowIntent(pkg),
            windowEventFilter = { event ->
                // Some MIUI14 devices send the change event for the system settings app
                event.pkgId == SETTINGS_PKG_HYPEROS || event.pkgId == SETTINGS_PKG_AOSP
            },
            windowNodeTest = {
                when {
                    windowCriteriaAppIdentifier(SETTINGS_PKG_HYPEROS, ipcFunnel, pkg)(it) -> {
                        windowPkg = SETTINGS_PKG_HYPEROS
                        true
                    }

                    windowCriteriaAppIdentifier(SETTINGS_PKG_AOSP, ipcFunnel, pkg)(it) -> {
                        windowPkg = SETTINGS_PKG_AOSP
                        true
                    }

                    else -> {
                        log(TAG) { "Unknown window: ${it.pkgId}" }
                        false
                    }
                }
            },
        )
        stepper.withProgress(this) { process(step) }

        log(TAG) { "Launched window pkg was $windowPkg" }
        when (windowPkg) {
            SETTINGS_PKG_AOSP -> settingsPlan(pkg)
            SETTINGS_PKG_HYPEROS -> securityCenterPlan(pkg)
            else -> throw IllegalStateException("Unknown window: $windowPkg")
        }
    }

    private val settingsPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = { pkg ->
        log(TAG, INFO) { "Executing AOSP settings plan for ${pkg.installId} with context $this" }

        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        run {
            val storageEntryLabels =
                aospLabels.getStorageEntryDynamic() + aospLabels.getStorageEntryStatic(lang, script)

            val storageFilter = onTheFlyLabler.getAOSPStorageFilter(storageEntryLabels, pkg)

            val step = StepProcessor.Step(
                source = TAG,
                descriptionInternal = "Storage entry (settings plan)",
                label = R.string.appcleaner_automation_progress_find_storage.toCaString(storageEntryLabels),
                nodeTest = storageFilter,
                nodeRecovery = getDefaultNodeRecovery(pkg),
                nodeMapping = clickableParent(),
                action = defaultClick()
            )
            stepper.withProgress(this) { process(step) }
        }

        run {
            val clearCacheButtonLabels =
                aospLabels.getClearCacheDynamic() + aospLabels.getClearCacheStatic(lang, script)

            val buttonFilter = fun(node: AccessibilityNodeInfo): Boolean {
                if (!node.isClickyButton()) return false
                return node.textMatchesAny(clearCacheButtonLabels)
            }

            val step = StepProcessor.Step(
                source = TAG,
                descriptionInternal = "Clear cache (settings plan)",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                nodeTest = buttonFilter,
                action = getAospClearCacheClick(pkg, tag)
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    private val securityCenterPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = { pkg ->
        log(TAG, INFO) { "Executing MIUI security center plan for ${pkg.installId} with context $this" }

        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script
        val country = locale.country

        log(VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script, country=$country)" }

        val clearDataLabels = hyperOsLabels.getClearDataButtonLabels(lang, script, country)
        log(TAG) { "clearDataLabels=$clearDataLabels" }
        val clearCacheLabels = hyperOsLabels.getClearCacheButtonLabels(lang, script, country)
        log(TAG) { "clearCacheLabels=$clearCacheLabels" }
        val dialogTitles = hyperOsLabels.getDialogTitles(lang, script, country)
        log(TAG) { "clearCacheLabels=$clearCacheLabels" }

        var useAlternativeStep = deviceAdminManager.getDeviceAdmins().contains(pkg.id).also {
            if (it) log(TAG) { "${pkg.id} is a device admin, using alternative step directly." }
        }

        if (!useAlternativeStep) {
            val clearDataFilter: suspend (AccessibilityNodeInfo) -> Boolean = filter@{ node ->
                if (!node.isTextView()) return@filter false
                if (node.textMatchesAny(clearDataLabels)) return@filter true
                if (node.textMatchesAny(clearCacheLabels)) {
                    useAlternativeStep = true
                    throw StepAbortException("Got 'Clear cache' instead of 'Clear data' skip the action dialog step.")
                }
                return@filter false
            }
            val step = StepProcessor.Step(
                source = TAG,
                descriptionInternal = "Clear data button (security center plan)",
                label = R.string.appcleaner_automation_progress_find_clear_data.toCaString(clearDataLabels),
                windowNodeTest = windowCriteriaAppIdentifier(SETTINGS_PKG_HYPEROS, ipcFunnel, pkg),
                nodeTest = clearDataFilter,
                nodeRecovery = getDefaultNodeRecovery(pkg),
                nodeMapping = clickableParent(),
                action = defaultClick()
            )
            stepper.withProgress(this) { process(step) }
        }

        if (useAlternativeStep) {
            val alternativeStep: StepProcessor.Step = StepProcessor.Step(
                source = TAG,
                descriptionInternal = "Clear cache button (alternative security center plan)",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheLabels),
                windowNodeTest = windowCriteriaAppIdentifier(SETTINGS_PKG_HYPEROS, ipcFunnel, pkg),
                nodeTest = { it.isTextView() && it.textMatchesAny(clearCacheLabels) },
                nodeMapping = clickableParent(),
                action = defaultClick()
            )
            stepper.withProgress(this) { process(alternativeStep) }
        } else {
            // This may be skipped when MIUI just shows a 'Clear cache' option

            // Clear data
            // -> Clear data
            // -> Clear cache
            // -> Cancel

            val windowCriteria = fun(node: AccessibilityNodeInfo): Boolean {
                if (node.pkgId != SETTINGS_PKG_HYPEROS) return false
                return node.crawl().map { it.node }.any { it.idContains("id/alertTitle") }
            }

            val versionCode = androidContext.packageManager.getPackageInfo2(SETTINGS_PKG_HYPEROS)?.let {
                PackageInfoCompat.getLongVersionCode(it)
            }
            val isWeird = versionCode?.let { it >= 40001065 } ?: false
            log(TAG) { "$SETTINGS_PKG_HYPEROS versionCode is $versionCode, isWeird=$isWeird" }

            val entryFilter = fun(node: AccessibilityNodeInfo): Boolean = when {
                isWeird && node.isRadioButton() -> node.textMatchesAny(clearCacheLabels)
                node.isTextView() && node.isClickable -> node.textMatchesAny(clearCacheLabels)
                else -> false
            }

            val step = StepProcessor.Step(
                source = TAG,
                descriptionInternal = "Clear cache button (security center plan)",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheLabels),
                windowNodeTest = windowCriteria,
                windowEventFilter = when {
                    isWeird -> waitUntilSettled {
                        it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                                && it.className == "android.widget.FrameLayout"
                                && it.pkgId == SETTINGS_PKG_HYPEROS
                    }

                    else -> null
                },
                nodeTest = entryFilter,
                action = when {
                    isWeird -> { node, _ -> gestureClick(node) }
                    else -> defaultClick()
                }
            )
            stepper.withProgress(this) { process(step) }
        }

        // Clear cache?
        // -> Cancel        -> Ok
        run {
            val windowCriteria = fun(node: AccessibilityNodeInfo): Boolean {
                if (node.pkgId != SETTINGS_PKG_HYPEROS) return false
                return node.crawl().map { it.node }.any { subNode ->
                    // This is required to relax the match on the dialog texts
                    // Otherwise it could detect the clear cache button as match
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

            val step = StepProcessor.Step(
                source = TAG,
                descriptionInternal = "Confirm clear cache (security center plan)",
                label = R.string.appcleaner_automation_progress_find_ok_confirmation.toCaString(""),
                windowNodeTest = windowCriteria,
                nodeTest = buttonFilter,
                action = defaultClick()
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: HyperOsSpecs): AppCleanerSpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "HyperOS", "Specs")
        private val SETTINGS_PKG_HYPEROS = "com.miui.securitycenter".toPkgId()
        private val SETTINGS_PKG_AOSP = "com.android.settings".toPkgId()

    }

}