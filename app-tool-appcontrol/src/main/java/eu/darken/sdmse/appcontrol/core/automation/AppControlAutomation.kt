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
import eu.darken.sdmse.appcontrol.core.archive.ArchiveAutomationTask
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelDebugger
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlSpecGenerator
import eu.darken.sdmse.appcontrol.core.automation.specs.androidtv.AndroidTVSpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.aosp.AOSPSpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.coloros.ColorOSSpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.hyperos.HyperOsSpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.miui.MIUISpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.oneui.OneUISpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.originos.OriginOSSpecs
import eu.darken.sdmse.appcontrol.core.automation.specs.oxygenos.OxygenOSSpecs
import eu.darken.sdmse.automation.core.ForceStopAutomationTask
import eu.darken.sdmse.appcontrol.core.restore.RestoreAutomationTask
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.AutomationModule
import eu.darken.sdmse.automation.core.AutomationReturnHelper
import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.automation.core.errors.AutomationCompatibilityException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.automation.core.finishAutomation
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.get
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Provider

class AppControlAutomation @AssistedInject constructor(
    @Assisted automationHost: AutomationHost,
    @Assisted private val moduleScope: CoroutineScope,
    private val pkgRepo: PkgRepo,
    private val automationExplorerFactory: AutomationExplorer.Factory,
    private val specGenerators: Provider<Set<@JvmSuppressWildcards AppControlSpecGenerator>>,
    private val userManager2: UserManager2,
    private val labelDebugger: AppControlLabelDebugger,
    private val deviceDetective: DeviceDetective,
    private val automationReturnHelper: AutomationReturnHelper,
) : AutomationModule(automationHost) {

    private enum class Operation {
        ARCHIVE,
        RESTORE;

        val unsupportedMessage: String
            get() = "ACS based ${name.lowercase()} is not supported for other users"
    }

    private fun getPriotizedSpecGenerators(): List<AppControlSpecGenerator> = specGenerators
        .get()
        .also { log(TAG) { "${it.size} step generators are available" } }
        .onEach { log(TAG, VERBOSE) { "Loaded: $it" } }
        .sortedByDescending { generator: AppControlSpecGenerator ->
            when (generator) {
                is MIUISpecs -> 190
                is HyperOsSpecs -> 180
                is OneUISpecs -> 170
//                is AlcatelSpecs -> 160
//                is RealmeSpecs -> 150
//                is HuaweiSpecs -> 140
//                is LGESpecs -> 130
                is ColorOSSpecs -> 110
//                is FlymeSpecs -> 100
                is OriginOSSpecs -> 80
                is AndroidTVSpecs -> 70
//                is NubiaSpecs -> 60
                is OxygenOSSpecs -> 30
//                is OnePlusSpecs -> 30
//                is HonorSpecs -> 20
                is AOSPSpecs -> -5
                else -> 0
            }
        }

    override suspend fun process(task: AutomationTask): AutomationTask.Result {
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)

        val subtitle = when (task) {
            is ForceStopAutomationTask -> eu.darken.sdmse.appcontrol.R.string.appcontrol_automation_subtitle_force_stopping
            is ArchiveAutomationTask -> eu.darken.sdmse.appcontrol.R.string.appcontrol_automation_subtitle_archiving
            is RestoreAutomationTask -> eu.darken.sdmse.appcontrol.R.string.appcontrol_automation_subtitle_restoring
            else -> eu.darken.sdmse.appcontrol.R.string.appcontrol_automation_subtitle_force_stopping
        }

        host.changeOptions {
            AutomationHost.Options(
                showOverlay = true,
                passthrough = false,
                controlPanelTitle = eu.darken.sdmse.appcontrol.R.string.appcontrol_automation_title.toCaString(),
                controlPanelSubtitle = subtitle.toCaString(),
                accessibilityServiceInfo = AccessibilityServiceInfo().apply {
                    flags = (
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                                    or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                                    or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                            )
                    eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                },
            )
        }

        return when (task) {
            is ForceStopAutomationTask -> processForceStop(task)
            is ArchiveAutomationTask -> {
                val (successful, failed) = processOperation(task.targets, Operation.ARCHIVE)
                ArchiveAutomationTask.Result(successful, failed)
            }

            is RestoreAutomationTask -> {
                val (successful, failed) = processOperation(task.targets, Operation.RESTORE)
                RestoreAutomationTask.Result(successful, failed)
            }

            else -> throw NotImplementedError("$task is not implemented")
        }
    }

    private suspend fun processForceStop(task: ForceStopAutomationTask): ForceStopAutomationTask.Result {
        labelDebugger.logAllLabels()

        updateProgressCount(Progress.Count.Percent(task.targets.size))

        var result: AutomationLoopResult? = null
        try {
            result = automateEachTarget(
                targets = task.targets,
                currentUserHandle = userManager2.currentUser().handle,
                actionLabel = "Force stopping",
                unsupportedForOtherUsers = "ACS based force-stop is not supported for other users",
                offLimitPkgs = when {
                    task.allowOffLimits -> emptySet()
                    else -> ForceStopAutomationTask.OFF_LIMIT_PKGS
                },
                resolveOne = { pkgRepo.get(it.pkgId, it.userHandle) },
                processOne = { processSpecForPkg(it) },
            )
        } finally {
            val userCancelled = result?.cancelledByUser ?: false
            // Run cleanup even when cancelled, otherwise a user cancel skips the back-navigation
            // and leaves the user stranded on the system settings screen.
            withContext(NonCancellable) {
                delay(250)

                finishAutomation(
                    userCancelled = userCancelled,
                    returnToAppIntent = automationReturnHelper.createReturnToAppIntent(context),
                    deviceDetective = deviceDetective,
                )
            }
        }

        // Make the "user cancel => full stop" contract explicit instead of relying on the
        // cancelled job tripping a suspension point above.
        if (result.cancelledByUser) throw UserCancelledAutomationException()

        if (result.gaveUp) {
            log(TAG, ERROR) { "Force-stop kept failing, possible compatibility issue?" }
            throw AutomationCompatibilityException()
        }

        return ForceStopAutomationTask.Result(
            successful = result.successful,
            failed = result.failed.keys,
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

    private suspend fun processOperation(
        targets: List<InstallId>,
        operation: Operation,
    ): Pair<Set<InstallId>, Map<InstallId, Exception>> {
        labelDebugger.logAllLabels()

        val operationName = operation.name.lowercase()

        updateProgressCount(Progress.Count.Percent(targets.size))

        var result: AutomationLoopResult? = null
        try {
            result = automateEachTarget(
                targets = targets,
                currentUserHandle = userManager2.currentUser().handle,
                actionLabel = "${operationName.replaceFirstChar { it.uppercase() }}ing",
                unsupportedForOtherUsers = operation.unsupportedMessage,
                resolveOne = { pkgRepo.get(it.pkgId, it.userHandle) },
                processOne = { processOperationSpecForPkg(it, operation) },
            )
        } finally {
            val userCancelled = result?.cancelledByUser ?: false
            withContext(NonCancellable) {
                delay(250)

                finishAutomation(
                    userCancelled = userCancelled,
                    returnToAppIntent = automationReturnHelper.createReturnToAppIntent(context),
                    deviceDetective = deviceDetective,
                )
            }
        }

        if (result.gaveUp) {
            log(TAG, ERROR) { "The $operationName automation kept failing, possible compatibility issue?" }
            throw AutomationCompatibilityException()
        }

        return result.successful to result.failed
    }

    private suspend fun processOperationSpecForPkg(pkg: Installed, operation: Operation) {
        val operationName = operation.name.lowercase()
        log(TAG) { "Processing $operationName spec for $pkg" }
        val start = System.currentTimeMillis()

        val specGenerator = getPriotizedSpecGenerators().firstOrNull { it.isResponsible(pkg) }
            ?: getPriotizedSpecGenerators().single { it is AOSPSpecs }

        log(TAG) { "Using spec generator: $specGenerator" }

        val spec = try {
            when (operation) {
                Operation.ARCHIVE -> specGenerator.getArchive(pkg)
                Operation.RESTORE -> specGenerator.getRestore(pkg)
            }
        } catch (e: UnsupportedOperationException) {
            log(TAG, WARN) { "Spec $specGenerator doesn't support $operationName, falling back to AOSP: ${e.message}" }
            val aospSpec = getPriotizedSpecGenerators().single { it is AOSPSpecs }
            when (operation) {
                Operation.ARCHIVE -> aospSpec.getArchive(pkg)
                Operation.RESTORE -> aospSpec.getRestore(pkg)
            }
        }
        log(TAG) { "Generated $operationName spec for ${pkg.id} is $spec" }

        when (spec) {
            is AutomationSpec.Explorer -> processExplorerSpec(pkg, spec)
        }

        val stop = System.currentTimeMillis()
        log(TAG) { "${operationName.replaceFirstChar { it.uppercase() }} spec processed in ${(stop - start)}ms for $pkg " }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): AutomationModule.Factory
    }

    @AssistedFactory
    interface Factory : AutomationModule.Factory {
        override fun isResponsible(task: AutomationTask): Boolean =
            task is ForceStopAutomationTask || task is ArchiveAutomationTask || task is RestoreAutomationTask

        override fun create(host: AutomationHost, moduleScope: CoroutineScope): AppControlAutomation
    }

    companion object {
        val TAG: String = logTag("Automation", "AppControl", "Module")
    }
}