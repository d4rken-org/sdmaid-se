package eu.darken.sdmse.appcontrol.core.automation

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
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelDebugger
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlSpecGenerator
import eu.darken.sdmse.appcontrol.core.automation.specs.androidtv.AndroidTVSpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.aosp.AOSPSpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.samsung.SamsungSpecs
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopAutomationTask
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.AutomationModule
import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.automation.core.errors.ScreenUnavailableException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.automation.core.finishAutomation
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getPkg
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import javax.inject.Provider

class AppControlAutomation @AssistedInject constructor(
    @Assisted automationHost: AutomationHost,
    @Assisted private val moduleScope: CoroutineScope,
    private val pkgRepo: PkgRepo,
    private val automationExplorerFactory: AutomationExplorer.Factory,
    private val specGenerators: Provider<Set<@JvmSuppressWildcards AppControlSpecGenerator>>,
    private val userManager2: UserManager2,
    private val labelDebugger: AppControlLabelDebugger,
) : AutomationModule(automationHost) {

    private fun getPriotizedSpecGenerators(): List<AppControlSpecGenerator> = specGenerators
        .get()
        .also { log(TAG) { "${it.size} step generators are available" } }
        .onEach { log(TAG, VERBOSE) { "Loaded: $it" } }
        .sortedByDescending { generator: AppControlSpecGenerator ->
            when (generator) {
//                is MIUISpecs -> 190
                is SamsungSpecs -> 170
//                is AlcatelSpecs -> 160
//                is RealmeSpecs -> 150
//                is HuaweiSpecs -> 140
//                is LGESpecs -> 130
//                is ColorOSSpecs -> 110
//                is FlymeSpecs -> 100
//                is VivoSpecs -> 80
                is AndroidTVSpecs -> 70
//                is NubiaSpecs -> 60
//                is OnePlusSpecs -> 30
//                is HonorSpecs -> 20
                is AOSPSpecs -> -5
                else -> 0
            }
        }

    override suspend fun process(task: AutomationTask): AutomationTask.Result {
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
                controlPanelTitle = R.string.appcontrol_automation_title.toCaString(),
                controlPanelSubtitle = R.string.appcontrol_automation_subtitle_force_stopping.toCaString(),
            )
        }

        return when (task) {
            is ForceStopAutomationTask -> processForceStop(task)
            else -> throw NotImplementedError("$task is not implemented")
        }
    }

    private suspend fun processForceStop(task: ForceStopAutomationTask): ForceStopAutomationTask.Result {
        labelDebugger.logAllLabels()

        val successful = mutableSetOf<Installed.InstallId>()
        val failed = mutableSetOf<Installed.InstallId>()

        updateProgressCount(Progress.Count.Percent(task.targets.size))

        var cancelledByUser = false
        val currentUserHandle = userManager2.currentUser().handle
        for (target in task.targets) {
            if (target.userHandle != currentUserHandle) {
                throw UnsupportedOperationException("ACS based force-stop is not support for other users ($target)")
            }

            val installed = pkgRepo.getPkg(target.pkgId, target.userHandle)

            if (installed == null) {
                log(TAG, WARN) { "$target is not in package repo" }
                failed.add(target)
                continue
            }

            log(TAG) { "Force stopping $installed" }
            updateProgressPrimary(installed.label ?: target.pkgId.name.toCaString())

            try {
                processSpecForPkg(installed)
                log(TAG, INFO) { "Successfully force-stopped $target" }
                successful.add(target)
            } catch (e: ScreenUnavailableException) {
                log(TAG, WARN) { "Cancelled because screen become unavailable: ${e.asLog()}" }
                // TODO We don't have to abort here, but this is not a normal state and should show an error?
                throw e
            } catch (e: TimeoutCancellationException) {
                log(TAG, WARN) { "Timeout while processing $installed" }
                failed.add(target)
            } catch (e: CancellationException) {
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
            } catch (e: Exception) {
                log(TAG, WARN) { "Failure for $target: ${e.asLog()}" }
                if (e is UnsupportedOperationException) throw e
                failed.add(target)
            } finally {
                updateProgressCount(Progress.Count.Percent(task.targets.indexOf(target), task.targets.size))
            }
        }

        delay(250)

        finishAutomation(
            userCancelled = cancelledByUser,
            returnToApp = true,
        )

        return ForceStopAutomationTask.Result(
            successful = successful,
            failed = failed,
        )
    }

    private suspend fun processSpecForPkg(pkg: Installed) {
        log(TAG) { "Processing spec for $pkg" }
        val start = System.currentTimeMillis()

        val specGenerator = getPriotizedSpecGenerators().firstOrNull { it.isResponsible(pkg) }
            ?: getPriotizedSpecGenerators().single { it is AOSPSpecs }

        log(TAG) { "Using spec generator: $specGenerator" }

        val spec = specGenerator.getForceStop(pkg)
        log(TAG) { "Generated spec for ${pkg.id} is $spec" }

        when (spec) {
            is AutomationSpec.Explorer -> processExplorerSpec(pkg, spec)
        }

        val stop = System.currentTimeMillis()
        log(TAG) { "Spec processed in ${(stop - start)}ms for $pkg " }
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
        override fun isResponsible(task: AutomationTask): Boolean = task is ForceStopAutomationTask

        override fun create(host: AutomationHost, moduleScope: CoroutineScope): AppControlAutomation
    }

    companion object {
        val TAG: String = logTag("Automation", "AppControl", "Module")
    }
}