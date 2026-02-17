package eu.darken.sdmse.appcleaner.core.automation

import android.accessibilityservice.AccessibilityServiceInfo
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
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerSpecGenerator
import eu.darken.sdmse.appcleaner.core.automation.specs.LabelDebugger
import eu.darken.sdmse.appcleaner.core.automation.specs.alcatel.AlcatelSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.androidtv.AndroidTVSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.coloros.ColorOSSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.flyme.FlymeSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.funtouchos.FuntouchOSSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.honor.HonorSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.huawei.HuaweiSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.hyperos.HyperOsSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.lge.LGESpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.miui.MIUISpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.nubia.NubiaSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.oneui.OneUISpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.oxygenos.OxygenOSSpecs
import eu.darken.sdmse.appcleaner.core.automation.specs.realme.RealmeSpecs
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.AutomationModule
import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.automation.core.errors.AutomationCompatibilityException
import eu.darken.sdmse.automation.core.errors.AutomationOverlayException
import eu.darken.sdmse.automation.core.errors.AutomationTimeoutException
import eu.darken.sdmse.automation.core.errors.InvalidSystemStateException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.automation.core.finishAutomation
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.OpsCounter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.get
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import javax.inject.Provider

class ClearCacheModule @AssistedInject constructor(
    @Assisted automationHost: AutomationHost,
    @Assisted private val moduleScope: CoroutineScope,
    val ipcFunnel: IPCFunnel,
    private val pkgRepo: PkgRepo,
    private val automationExplorerFactory: AutomationExplorer.Factory,
    private val specGenerators: Provider<Set<@JvmSuppressWildcards AppCleanerSpecGenerator>>,
    private val userManager2: UserManager2,
    private val labelDebugger: LabelDebugger,
    private val deviceDetective: DeviceDetective,
    private val generalSettings: GeneralSettings,
) : AutomationModule(automationHost) {

    private fun getPriotizedSpecGenerators(): List<AppCleanerSpecGenerator> = specGenerators
        .get()
        .also { log(TAG) { "${it.size} step generators are available" } }
        .onEach { log(TAG, VERBOSE) { "Loaded: $it (${it.tag})" } }
        .sortedByDescending { generator: AppCleanerSpecGenerator ->
            when (generator) {
                is MIUISpecs -> 190
                is HyperOsSpecs -> 180
                is OneUISpecs -> 170
                is AlcatelSpecs -> 160
                is RealmeSpecs -> 150
                is HuaweiSpecs -> 140
                is LGESpecs -> 130
                is ColorOSSpecs -> 110
                is FlymeSpecs -> 100
                is FuntouchOSSpecs -> 80
                is AndroidTVSpecs -> 70
                is NubiaSpecs -> 60
                is OxygenOSSpecs -> 30
                is HonorSpecs -> 20
                is AOSPSpecs -> -5
                else -> 0
            }
        }

    override suspend fun process(task: AutomationTask): AutomationTask.Result {
        task as ClearCacheTask
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)

        host.changeOptions {
            AutomationHost.Options(
                showOverlay = true,
                passthrough = false,
                controlPanelTitle = R.string.appcleaner_automation_title.toCaString(),
                controlPanelSubtitle = R.string.appcleaner_automation_subtitle_default_caches.toCaString(),
                accessibilityServiceInfo = AccessibilityServiceInfo().apply {
                    flags = (
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                                    or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                                    or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                            )
                    eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                    notificationTimeout = 250L
                },
            )
        }

        labelDebugger.logAllLabels()

        var result: ProcessedTask? = null
        try {
            result = processTask(task)
        } finally {
            finishAutomation(
                userCancelled = result?.cancelledByUser ?: false,
                returnToApp = task.returnToApp,
                deviceDetective = deviceDetective,
            )
        }

        if (Bugs.isDebug) {
            result.failed.forEach { (id, e) -> log(TAG, WARN) { "$id failed with $e" } }
        }

        val timeoutCount = result.failed.count { it.value is AutomationTimeoutException }
        if (timeoutCount >= TIMEOUT_LIMIT && result.successful.isEmpty()) {
            log(TAG, ERROR) { "Continued timeout errors, no successes so far, possible compatbility issue?" }
            throw AutomationCompatibilityException()
        }

        return ClearCacheTask.Result(
            successful = result.successful,
            failed = result.failed
        )
    }

    private data class ProcessedTask(
        val successful: Collection<InstallId>,
        val failed: Map<InstallId, Exception>,
        val cancelledByUser: Boolean,
    )

    private suspend fun processTask(task: ClearCacheTask): ProcessedTask {
        val successful = mutableSetOf<InstallId>()
        val failed = mutableMapOf<InstallId, Exception>()

        updateProgressCount(Progress.Count.Percent(task.targets.size))

        var cancelledByUser = false
        val currentUserHandle = userManager2.currentUser().handle

        var lastTarget: Pkg.Id? = null
        val opsCounter = OpsCounter { opsRate, lastOps ->
            log(TAG, if (lastOps > 3000) WARN else VERBOSE) {
                "Processing performance $opsRate pkgs/s (${lastOps}ms for $lastTarget)"
            }
        }

        for (target in task.targets) {
            lastTarget = target.pkgId
            if (target.userHandle != currentUserHandle) {
                throw UnsupportedOperationException("ACS based deletion is not support for other users ($target)")
            }

            val installed = pkgRepo.get(target.pkgId, target.userHandle)

            if (installed == null) {
                log(TAG, WARN) { "$target is not in package repo" }
                failed[target] = IllegalStateException("$target is not in package repo")
                continue
            }

            log(TAG) { "Clearing cache for $installed" }
            updateProgressPrimary(installed.label ?: target.pkgId.name.toCaString())

            try {
                processSpecForPkg(installed)
                log(TAG, INFO) { "Successfully cleared cache for for $target" }
                task.onSuccess(target)
                successful.add(target)
            } catch (e: Exception) {
                when {
                    e is InvalidSystemStateException -> {
                        log(TAG, WARN) { "Invalid system state for ACS based cache deletion: ${e.asLog()}" }
                        throw e
                    }

                    e is AutomationTimeoutException -> {
                        log(TAG, WARN) { "Timeout while processing $installed" }
                        task.onError(target, e)
                        failed[target] = e
                        val timeouts = failed.count { it.value is AutomationTimeoutException }
                        if (successful.isEmpty() && timeouts > TIMEOUT_LIMIT) break
                    }

                    e is AutomationOverlayException -> {
                        log(TAG, ERROR) { "Automation overlay error: ${e.asLog()}" }
                        throw e
                    }

                    e is PlanAbortException && e.treatAsSuccess -> {
                        log(TAG, INFO) { "Treating aborted plan as success for $target:\n${e.asLog()}" }
                        successful.add(target)
                    }

                    e is CancellationException -> {
                        log(TAG, WARN) { "We were cancelled: ${e.asLog()}" }
                        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_cancel_action)
                        updateProgressSecondary(CaString.EMPTY)
                        updateProgressCount(Progress.Count.Indeterminate())
                        if (e is UserCancelledAutomationException) {
                            log(TAG, INFO) { "User has cancelled automation process, aborting..." }
                            cancelledByUser = true
                            break
                        } else {
                            throw e
                        }
                    }

                    else -> {
                        log(TAG, WARN) { "Failure for $target:\n${e.asLog()}" }
                        task.onError(target, e)
                        failed[target] = e
                    }
                }
            } finally {
                increaseProgress()
                opsCounter.tick()
            }
        }

        return ProcessedTask(
            successful = successful,
            failed = failed,
            cancelledByUser = cancelledByUser,
        )
    }

    private suspend fun processSpecForPkg(pkg: Installed) {
        log(TAG) { "Clearing default primary caches for $pkg" }
        val start = System.currentTimeMillis()
        val romTypeDetection = generalSettings.romTypeDetection.value()
        log(TAG, INFO) { "romTypeDetection=$romTypeDetection" }

        val specGenerator = getPriotizedSpecGenerators().firstOrNull { it.isResponsible(pkg) }
            ?: getPriotizedSpecGenerators().single { it is AOSPSpecs }
        log(TAG) { "Using spec generator: ${specGenerator.tag}" }

        val spec = specGenerator.getClearCache(pkg)
        log(TAG) { "Generated spec for ${pkg.id} is ${spec.tag}" }

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
            onUpdate = { existing, new -> existing?.copy(secondary = new?.primary ?: CaString.EMPTY) },
            onCompletion = { current -> current }
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
        private const val TIMEOUT_LIMIT = 8
        val TAG: String = logTag("Automation", "AppCleaner", "ClearCacheModule")
    }
}