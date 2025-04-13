package eu.darken.sdmse.appcleaner.core.automation.specs.hyperos

import android.graphics.Rect
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
import eu.darken.sdmse.appcleaner.core.automation.specs.defaultFindAndClickClearCache
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.getSysLocale
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.idMatches
import eu.darken.sdmse.automation.core.common.isClickyButton
import eu.darken.sdmse.automation.core.common.isTextView
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.common.stepper.clickGesture
import eu.darken.sdmse.automation.core.common.stepper.clickNormal
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.stepper.findNode
import eu.darken.sdmse.automation.core.common.textEndsWithAny
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.checkAppIdentifier
import eu.darken.sdmse.automation.core.specs.defaultFindAndClick
import eu.darken.sdmse.automation.core.specs.defaultNodeRecovery
import eu.darken.sdmse.automation.core.specs.windowCheck
import eu.darken.sdmse.automation.core.specs.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.specs.windowLauncherDefaultSettings
import eu.darken.sdmse.automation.core.waitForWindowRoot
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs.isDryRun
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
    private val stepper: Stepper,
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

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = plan@{ pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        var windowPkg: Pkg.Id? = null

        val windowCheck: suspend StepContext.() -> AccessibilityNodeInfo = {
            if (stepAttempts >= 1 && pkg.hasNoSettings) {
                throw PlanAbortException("${pkg.packageName} has no settings window.")
            }
            // Wait for correct base window
            host.events
                .filter { event -> event.pkgId == SETTINGS_PKG_HYPEROS || event.pkgId == SETTINGS_PKG_AOSP }
                .mapNotNull { host.windowRoot() }
                .first { root ->
                    when {
                        root.pkgId == SETTINGS_PKG_HYPEROS && checkAppIdentifier(ipcFunnel, pkg)(root) -> {
                            windowPkg = SETTINGS_PKG_HYPEROS
                            true
                        }

                        root.pkgId == SETTINGS_PKG_AOSP && checkAppIdentifier(ipcFunnel, pkg)(root) -> {
                            windowPkg = SETTINGS_PKG_AOSP
                            true
                        }

                        else -> {
                            log(TAG) { "Unknown window: ${root.pkgId}" }
                            false
                        }
                    }
                }
        }

        val step = AutomationStep(
            source = TAG,
            descriptionInternal = "Storage entry (main plan)",
            label = R.string.appcleaner_automation_progress_find_storage.toCaString(""),
            windowLaunch = windowLauncherDefaultSettings(pkg),
            windowCheck = windowCheck,
        )
        stepper.withProgress(this) { process(this@plan, step) }

        log(TAG) { "Launched window pkg was $windowPkg" }
        when (windowPkg) {
            SETTINGS_PKG_AOSP -> settingsPlan(pkg)
            SETTINGS_PKG_HYPEROS -> securityCenterPlan(pkg)
            else -> throw IllegalStateException("Unknown window: $windowPkg")
        }
    }

    private val settingsPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = plan@{ pkg ->
        log(TAG, INFO) { "Executing AOSP settings plan for ${pkg.installId} with context $this" }

        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        run {
            val storageEntryLabels =
                aospLabels.getStorageEntryDynamic() + aospLabels.getStorageEntryStatic(lang, script)

            val storageFilter = onTheFlyLabler.getAOSPStorageFilter(storageEntryLabels, pkg)

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Storage entry (settings plan)",
                label = R.string.appcleaner_automation_progress_find_storage.toCaString(storageEntryLabels),
                nodeRecovery = defaultNodeRecovery(pkg),
                nodeAction = defaultFindAndClick { storageFilter(it) },
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        run {
            val clearCacheButtonLabels =
                aospLabels.getClearCacheDynamic() + aospLabels.getClearCacheStatic(lang, script)

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Clear cache (settings plan)",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                nodeAction = defaultFindAndClickClearCache(isDryRun = isDryRun, pkg) {
                    if (!it.isClickyButton()) false else it.textMatchesAny(clearCacheButtonLabels)
                },
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    private val securityCenterPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = plan@{ pkg ->
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
        val manageSpaceLabels = hyperOsLabels.getManageSpaceButtonLabels(lang, script, country)
        log(TAG) { "manageSpaceLabels=$manageSpaceLabels" }

        var useAlternativeStep = deviceAdminManager.getDeviceAdmins().contains(pkg.id).also {
            if (it) log(TAG) { "${pkg.id} is a device admin, using alternative step directly." }
        }

        if (!useAlternativeStep) {
            val action: suspend StepContext.() -> Boolean = action@{
                val clearDataTarget = findNode { it.isTextView() && it.textMatchesAny(clearDataLabels) }

                if (clearDataTarget == null) {
                    findNode { it.isTextView() && it.textMatchesAny(clearCacheLabels) }?.let {
                        useAlternativeStep = true
                        throw StepAbortException("Got 'Clear cache' instead of 'Clear data' skip the action dialog step.")
                    }

                    findNode { it.isTextView() && it.textMatchesAny(manageSpaceLabels) }?.let {
                        if (pkg.applicationInfo?.manageSpaceActivityName != null) {
                            throw PlanAbortException(
                                message = "Got 'Manage space'. App has no cache, skipping.",
                                treatAsSuccess = true,
                            )
                        }
                    }

                    return@action false
                }

                val mapped = findClickableParent(node = clearDataTarget) ?: return@action false
                clickNormal(node = mapped)
            }

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Clear data button (security center plan)",
                label = R.string.appcleaner_automation_progress_find_clear_data.toCaString(clearDataLabels),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG_HYPEROS, ipcFunnel, pkg),
                nodeRecovery = defaultNodeRecovery(pkg),
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        if (useAlternativeStep) {
            val alternativeStep = AutomationStep(
                source = TAG,
                descriptionInternal = "Clear cache button (alternative security center plan)",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheLabels),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG_HYPEROS, ipcFunnel, pkg),
                nodeAction = defaultFindAndClick {
                    it.isTextView() && it.textMatchesAny(clearCacheLabels)
                },
            )
            stepper.withProgress(this) { process(this@plan, alternativeStep) }
        } else {
            // This may be skipped when MIUI just shows a 'Clear cache' option

            // Clear data
            // -> Clear data
            // -> Clear cache
            // -> Cancel

            val windowCheck: suspend StepContext.() -> AccessibilityNodeInfo = {
                // Wait till the dialog is shown
                host.events.first { event ->
                    val root = host.windowRoot() ?: return@first false
                    if (root.pkgId != SETTINGS_PKG_HYPEROS) return@first false
                    root.crawl().map { it.node }.any { it.idContains("id/alertTitle") }
                }
                log(TAG) { "Got the right window, now waiting for dialog to settle..." }
                // Now we have to make sure the BottomSheetDialog animation is settled
                host.events
                    .mapNotNull { host.windowRoot() }
                    .mapNotNull { root ->
                        root.crawl().map { it.node }.singleOrNull {
                            it.textMatchesAny(clearCacheLabels)
                        }
                    }
                    .map { Rect().apply { it.getBoundsInScreen(this) } }
                    .distinctUntilChanged()
                    .debounce(100)
                    .first()
                host.waitForWindowRoot()
            }

            val settingsPkgInfo = androidContext.packageManager.getPackageInfo2(SETTINGS_PKG_HYPEROS)
            val versionCode = settingsPkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) }
            val versionName = settingsPkgInfo?.versionName

            /**
             * First it was a clickable TextView
             * Then an unclickable RadioButton
             * Now it is usually an unclickable TextView, wtf Xiaomi
             */
            val action: suspend StepContext.() -> Boolean = action@{
                var needsClickGesture = false
                val target = findNode { node ->
                    if (!node.textMatchesAny(clearCacheLabels)) return@findNode false
                    needsClickGesture = !node.isClickable
                    log(TAG) { "needsClickGesture=$needsClickGesture Version is $versionName ($versionCode)" }
                    true
                }
                if (target == null) return@action false
                when {
                    needsClickGesture -> clickGesture(node = target)
                    else -> clickNormal(node = target)
                }
            }

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Clear cache button (security center plan)",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheLabels),
                windowCheck = windowCheck,
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        // Clear cache?
        // -> Cancel        -> Ok
        run {
            val windowCheck = windowCheck { event, root ->
                if (root.pkgId != SETTINGS_PKG_HYPEROS) return@windowCheck false
                return@windowCheck root.crawl().map { it.node }.any { subNode ->
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

            val action: suspend StepContext.() -> Boolean = action@{
                val target = findNode { node ->
                    if (!node.isClickyButton()) false
                    else when (isDryRun) {
                        true -> node.idMatches("android:id/button2")
                        false -> node.idMatches("android:id/button1")
                    }

                } ?: return@action false
                clickNormal(isDryRun = isDryRun, node = target)
            }

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Confirm clear cache (security center plan)",
                label = R.string.appcleaner_automation_progress_find_ok_confirmation.toCaString(""),
                windowCheck = windowCheck,
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
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