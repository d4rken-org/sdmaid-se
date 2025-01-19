package eu.darken.sdmse.appcleaner.core.automation.specs.vivo

import android.view.accessibility.AccessibilityNodeInfo
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.OnTheFlyLabler
import eu.darken.sdmse.automation.core.common.StepProcessor
import eu.darken.sdmse.automation.core.common.clickableParent
import eu.darken.sdmse.automation.core.common.defaultClick
import eu.darken.sdmse.automation.core.common.defaultWindowFilter
import eu.darken.sdmse.automation.core.common.defaultWindowIntent
import eu.darken.sdmse.automation.core.common.getAospClearCacheClick
import eu.darken.sdmse.automation.core.common.getDefaultNodeRecovery
import eu.darken.sdmse.automation.core.common.getSysLocale
import eu.darken.sdmse.automation.core.common.idContains
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.common.windowCriteriaAppIdentifier
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

@Reusable
class VivoSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val deviceDetective: DeviceDetective,
    private val vivoLabels: VivoLabels,
    private val onTheFlyLabler: OnTheFlyLabler,
    private val generalSettings: GeneralSettings,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.VIVO) return true
        if (romType != RomType.AUTO) return false

        return deviceDetective.getROMType() == RomType.VIVO
    }

    override suspend fun getClearCache(pkg: Installed): AutomationSpec = object : AutomationSpec.Explorer {
        override val tag: String = TAG
        override suspend fun createPlan(): suspend AutomationExplorer.Context.() -> Unit = {
            mainPlan(pkg)
        }
    }

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = { pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        val locale = getSysLocale()
        val lang = locale.language
        val script = locale.script

        log(VERBOSE) { "Getting specs for ${pkg.packageName} (lang=$lang, script=$script)" }

        run {
            val storageEntryLabels =
                vivoLabels.getStorageEntryDynamic() + vivoLabels.getStorageEntryStatic(lang, script)
            log(TAG) { "storageEntryLabels=$storageEntryLabels" }

            val storageFilter = onTheFlyLabler.getAOSPStorageFilter(storageEntryLabels, pkg)

            val step = StepProcessor.Step(
                source = TAG,
                descriptionInternal = "Storage entry",
                label = R.string.appcleaner_automation_progress_find_storage.toCaString(storageEntryLabels),
                windowIntent = defaultWindowIntent(pkg),
                windowEventFilter = defaultWindowFilter(SETTINGS_PKG),
                windowNodeTest = windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = storageFilter,
                nodeRecovery = getDefaultNodeRecovery(pkg),
                nodeMapping = clickableParent(
                    maxNesting = when {
                        hasApiLevel(29) -> 4
                        else -> 6
                    }
                ),
                action = defaultClick()
            )
            stepper.withProgress(this) { process(step) }
        }

        run {
            val clearCacheButtonLabels =
                vivoLabels.getClearCacheDynamic() + vivoLabels.getClearCacheStatic(lang, script)
            log(TAG) { "clearCacheButtonLabels=$clearCacheButtonLabels" }

            var isUnclickableLabelButton = false
            val buttonFilter = when {
                hasApiLevel(34) -> fun(node: AccessibilityNodeInfo): Boolean {
                    if (!node.textMatchesAny(clearCacheButtonLabels)) return false

                    return if (node.idContains("id/vbutton_title")) {
                        isUnclickableLabelButton = true
                        true
                    } else {
                        node.isClickable
                    }
                }

                else -> fun(node: AccessibilityNodeInfo): Boolean {
                    return node.isClickable && node.textMatchesAny(clearCacheButtonLabels)
                }
            }

            val step = StepProcessor.Step(
                source = TAG,
                descriptionInternal = "Clear cache",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                windowNodeTest = windowCriteriaAppIdentifier(SETTINGS_PKG, ipcFunnel, pkg),
                nodeTest = buttonFilter,
                nodeMapping = when {
                    hasApiLevel(34) -> {
                        // Pass a function that is evaluated later, and has access to vars in this scope
                        { node ->
                            when {
                                isUnclickableLabelButton -> clickableParent().invoke(node)
                                else -> node
                            }
                        }
                    }

                    else -> null
                },
                action = getAospClearCacheClick(pkg, tag)
            )
            stepper.withProgress(this) { process(step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: VivoSpecs): AppCleanerSpecGenerator
    }

    companion object {
        val SETTINGS_PKG = "com.android.settings".toPkgId()

        val TAG: String = logTag("AppCleaner", "Automation", "Vivo", "Spec")
    }

}
