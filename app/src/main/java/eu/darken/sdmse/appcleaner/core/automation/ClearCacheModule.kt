package eu.darken.sdmse.appcleaner.core.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.*
import eu.darken.sdmse.appcleaner.core.automation.specs.alcatel.AlcatelSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.androidtv.AndroidTVSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.coloros.ColorOSSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.flyme.FlymeSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.huawei.HuaweiSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.lge.LGESpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.miui.MIUISpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.nubia.NubiaSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.oneplus.OnePlusSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.realme.RealmeSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.samsung.SamsungSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.vivo.VivoSpecs
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.AutomationModule
import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.automation.core.common.CrawlerCommon
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.SpecGenerator
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getPkg
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import javax.inject.Provider

class ClearCacheModule @AssistedInject constructor(
    @Assisted automationHost: AutomationHost,
    @Assisted private val moduleScope: CoroutineScope,
    val ipcFunnel: IPCFunnel,
    private val pkgRepo: PkgRepo,
    private val automationExplorerFactory: AutomationExplorer.Factory,
    private val specGenerators: Provider<Set<@JvmSuppressWildcards SpecGenerator>>,
    private val userManager2: UserManager2,
) : AutomationModule(automationHost) {

    private fun getPriotizedSpecGenerators(): List<SpecGenerator> = specGenerators
        .get()
        .also { log(TAG) { "${it.size} step generators are available" } }
        .onEach { log(TAG, VERBOSE) { "Loaded: $it" } }
        .sortedByDescending { generator: SpecGenerator ->
            when (generator) {
                is MIUISpecs -> 190
                is SamsungSpecs -> 170
                is AlcatelSpecs -> 160
                is RealmeSpecs -> 150
                is HuaweiSpecs -> 140
                is LGESpecs -> 130
                is ColorOSSpecs -> 110
                is FlymeSpecs -> 100
                is VivoSpecs -> 80
                is AndroidTVSpecs -> 70
                is NubiaSpecs -> 60
                is OnePlusSpecs -> 30
                is AOSPSpecs -> -5
                else -> 0
            }
        }

    override suspend fun process(task: AutomationTask): AutomationTask.Result {
        task as ClearCacheTask
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)

        host.changeOptions { old ->
            old.copy(
                showOverlay = true,
                accessibilityServiceInfo = AccessibilityServiceInfo().apply {
                    flags = (
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                                    or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                                    or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                            )
                    eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                },
                controlPanelTitle = R.string.appcleaner_automation_title.toCaString(),
                controlPanelSubtitle = R.string.appcleaner_automation_subtitle_default_caches.toCaString(),
            )
        }

        val successful = mutableSetOf<Installed.InstallId>()
        val failed = mutableSetOf<Installed.InstallId>()

        updateProgressCount(Progress.Count.Percent(task.targets.size))

        val currentUserHandle = userManager2.currentUser().handle
        for (target in task.targets) {
            if (target.userHandle != currentUserHandle) {
                throw UnsupportedOperationException("ACS based deletion is not support for other users ($target)")
            }

            val installed = pkgRepo.getPkg(target.pkgId, target.userHandle)

            if (installed == null) {
                log(TAG, WARN) { "$target is not in package repo" }
                failed.add(target)
                continue
            }

            log(TAG) { "Clearing cache for $installed" }
            updateProgressPrimary(installed.label ?: target.pkgId.name.toCaString())

            try {
                processSpecForPkg(installed)
                log(TAG, INFO) { "Successfully cleared cache for for $target" }
                successful.add(target)
            } catch (e: TimeoutCancellationException) {
                log(TAG, WARN) { "Timeout while processing $installed" }
                failed.add(target)
            } catch (e: CancellationException) {
                log(TAG, WARN) { "We were cancelled" }
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Failure for $target: ${e.asLog()}" }
                failed.add(target)
            } finally {
                increaseProgress()
            }
        }

        val backAction1 = host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        log(TAG, VERBOSE) { "Was back1 successful=$backAction1" }

        delay(500)

        val backAction2 = host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        log(TAG, VERBOSE) { "Was back2 successful=$backAction2" }

        return ClearCacheTask.Result(
            successful = successful,
            failed = failed,
        )
    }

    private suspend fun processSpecForPkg(pkg: Installed) {
        log(TAG) { "Clearing default primary caches for $pkg" }
        val start = System.currentTimeMillis()

        val specGenerator = getPriotizedSpecGenerators().firstOrNull { it.isResponsible(pkg) }
            ?: throw createUnsupportedError("DeviceSpec")
        log(TAG) { "Using spec generator: $specGenerator" }


        val spec = specGenerator.getSpec(pkg)
        log(TAG) { "Generated spec for ${pkg.id} is $spec" }

        when (spec) {
            is AutomationSpec.Explorer -> processExplorerSpec(pkg, spec)
        }

        val stop = System.currentTimeMillis()
        log(TAG) { "Cleared default primary cache in ${(stop - start)}ms for $pkg " }
    }

    private suspend fun processExplorerSpec(pkg: Installed, spec: AutomationSpec.Explorer) {
        log(TAG) { "processExplorerSpec($pkg, $spec)" }

        val explorer = automationExplorerFactory.create(host)
        explorer.withProgress(
            client = this,
            onUpdate = { new, existing -> existing?.copy(secondary = new?.primary ?: CaString.EMPTY) },
            onCompletion = { it }
        ) {
            process(spec)
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): AutomationModule.Factory
    }

    @AssistedFactory
    interface Factory : AutomationModule.Factory {
        override fun isResponsible(task: AutomationTask): Boolean = task is ClearCacheTask

        override fun create(host: AutomationHost, moduleScope: CoroutineScope): ClearCacheModule
    }

    companion object {
        val TAG: String = logTag("Automation", "AppCleaner", "ClearCacheModule")

        fun createUnsupportedError(tag: String): Throwable {
            val locale = CrawlerCommon.getSysLocale()
            val apiLevel = BuildWrap.VERSION.SDK_INT
            val rom = Build.MANUFACTURER
            return UnsupportedOperationException("$tag: ROM $rom($apiLevel) & Locale ($locale) is not supported.")
        }
    }
}