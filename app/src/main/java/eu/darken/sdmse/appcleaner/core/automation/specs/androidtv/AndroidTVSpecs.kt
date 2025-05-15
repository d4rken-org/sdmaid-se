package eu.darken.sdmse.appcleaner.core.automation.specs.androidtv

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.clickClearCache
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.idMatches
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.scrollNode
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.common.stepper.findClickableParent
import eu.darken.sdmse.automation.core.common.stepper.findNode
import eu.darken.sdmse.automation.core.common.textMatchesAny
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.defaultFindAndClick
import eu.darken.sdmse.automation.core.specs.windowCheck
import eu.darken.sdmse.automation.core.specs.windowCheckDefaultSettings
import eu.darken.sdmse.automation.core.specs.windowLauncherDefaultSettings
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.toVisualStrings
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject

@Reusable
open class AndroidTVSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val androidTVLabels: AndroidTVLabels,
    private val generalSettings: GeneralSettings,
    private val stepper: Stepper,
) : AppCleanerSpecGenerator {

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        val romType = generalSettings.romTypeDetection.value()
        if (romType == RomType.ANDROID_TV) return true
        if (romType != RomType.AUTO) return false

        return deviceDetective.getROMType() == RomType.ANDROID_TV
    }

    override suspend fun getClearCache(pkg: Installed): AutomationSpec = object : AutomationSpec.Explorer {
        override val tag: String = TAG
        override suspend fun createPlan(): suspend AutomationExplorer.Context.() -> Unit = {
            mainPlan(pkg)
        }
    }

    private val mainPlan: suspend AutomationExplorer.Context.(Installed) -> Unit = plan@{ pkg ->
        log(TAG, INFO) { "Executing plan for ${pkg.installId} with context $this" }

        run {
            val clearCacheButtonLabels = androidTVLabels.getClearCacheLabels(this)
            log(TAG) { "clearCacheButtonLabels=${clearCacheButtonLabels.toVisualStrings()}" }

            if (clearCacheButtonLabels.isEmpty()) {
                log(TAG, WARN) { "clearCacheButtonLabels was empty" }
                throw UnsupportedOperationException("This system language is not supported")
            }

            val action: suspend StepContext.() -> Boolean = action@{
                val target = findNode {
                    if (!it.idMatches("android:id/title")) false else it.textMatchesAny(clearCacheButtonLabels)
                } ?: return@action false

                val mapped = findClickableParent(node = target) ?: return@action false

                clickClearCache(isDryRun = false, pkg, node = mapped)
            }
            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Clear cache button",
                label = R.string.appcleaner_automation_progress_find_clear_cache.toCaString(clearCacheButtonLabels),
                windowLaunch = windowLauncherDefaultSettings(pkg),
                windowCheck = windowCheckDefaultSettings(SETTINGS_PKG, ipcFunnel, pkg),
                nodeRecovery = { it.scrollNode() },
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }

        run {
            val clearCacheTexts = androidTVLabels.getClearCacheLabels(this)
            log(TAG) { "clearCacheTexts=${clearCacheTexts.toVisualStrings()}" }

            val windowCheck = windowCheck { _, root ->
                if (root.pkgId != SETTINGS_PKG) return@windowCheck false
                root.crawl().map { it.node }.any { subNode ->
                    when {
                        subNode.idMatches("com.android.tv.settings:id/guidance_title") -> when {
                            subNode.textMatchesAny(clearCacheTexts) -> true
                            subNode.textMatchesAny(clearCacheTexts.map { it.replace("?", "") }) -> true
                            subNode.textMatchesAny(clearCacheTexts.map { "$it?" }) -> true
                            else -> false
                        }

                        subNode.idMatches("com.android.tv.settings:id/guidance_container") -> true
                        else -> false
                    }
                }
            }

            val buttonLabels = setOf(context.getString(android.R.string.ok))

            val action = defaultFindAndClick(
                isDryRun = Bugs.isDryRun,
                finder = {
                    findNode { node ->
                        when {
                            node.idMatches("com.android.tv.settings:id/guidedactions_item_content") -> true
                            node.idMatches("com.android.tv.settings:id/guidedactions_item_title") -> {
                                node.textMatchesAny(buttonLabels)
                            }

                            else -> false
                        }
                    }
                }
            )

            val step = AutomationStep(
                source = TAG,
                descriptionInternal = "Confirm action",
                label = R.string.appcleaner_automation_progress_find_ok_confirmation.toCaString(buttonLabels),
                windowCheck = windowCheck,
                nodeAction = action,
            )
            stepper.withProgress(this) { process(this@plan, step) }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AndroidTVSpecs): AppCleanerSpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "AndroidTV", "Specs")
        val SETTINGS_PKG = "com.android.tv.settings".toPkgId()

    }
}
