package eu.darken.sdmse.automation.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.automation.core.animation.AnimationTool
import eu.darken.sdmse.automation.core.errors.AutomationNoConsentException
import eu.darken.sdmse.automation.core.errors.AutomationNotEnabledException
import eu.darken.sdmse.automation.core.errors.AutomationNotRunningException
import eu.darken.sdmse.common.SystemSettingsProvider
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.shareLatest
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.useRes
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.ourInstall
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.SetupHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val settings: GeneralSettings,
    @AppScope private val appScope: CoroutineScope,
    private val setupHelper: SetupHelper,
    private val settingsProvider: SystemSettingsProvider,
    private val acsWriteReliability: AcsWriteReliability,
    private val animationTool: AnimationTool,
    private val shellOps: ShellOps,
    private val adbManager: AdbManager,
    private val rootManager: RootManager,
    private val pkgOps: PkgOps,
    private val userManager: UserManager2,
) : AutomationSubmitter {

    init {
        appScope.launch {
            try {
                animationTool.restorePendingState()
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to restore pending animation state on init: ${e.asLog()}" }
            }
        }
    }

    private val serviceHolder = MutableStateFlow<AutomationServiceHandle?>(null)
    val currentService: Flow<AutomationServiceHandle?> = serviceHolder

    internal fun setCurrentService(service: AutomationServiceHandle?) {
        log(TAG) { "setCurrentService($service)" }
        serviceHolder.value = service
    }

    override val useAcs: Flow<Boolean> = combine(
        settings.hasAcsConsent.flow,
        currentService,
    ) { consent, _ ->
        if (consent != true) return@combine false
        (isServiceEnabled() && isServiceLaunched()) || canSelfEnable()
    }.shareLatest(appScope)

    private val ourServiceComp: ComponentName by lazy {
        ComponentName(context, AutomationService::class.java)
    }

    private suspend fun getAutomationServices(): Set<ComponentName> {
        val setting = settingsProvider.get<String>(
            SystemSettingsProvider.Type.SECURE,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        return setting
            ?.split(":")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { ComponentName.unflattenFromString(it) }
            ?.toSet()
            ?: emptySet()
    }

    private suspend fun shellMode(): ShellOps.Mode? = when {
        adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
        rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
        else -> null
    }

    /** Writes the service list via our own process. Returns the post-write readback. */
    private suspend fun writeServicesDirect(services: Set<ComponentName>): Set<ComponentName> {
        settingsProvider.put(
            SystemSettingsProvider.Type.SECURE,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            AcsActivator.flattenServices(services)
        )
        val after = getAutomationServices()
        log(TAG) { "writeServicesDirect($services) -> $after" }
        return after
    }

    /**
     * Writes the service list via the privileged shell (Shizuku/ADB or root, uid 2000 / root). This is
     * not subject to the "untrusted app touched a protected setting" rollback some ROMs apply to writes
     * from our own process. Returns whether the intended list actually persisted.
     */
    private suspend fun writeServicesViaShell(services: Set<ComponentName>, mode: ShellOps.Mode): Boolean {
        try {
            pkgOps.setAppOps(
                userManager.ourInstall(),
                PkgOps.AppOpsKey.ACCESS_RESTRICTED_SETTINGS,
                PkgOps.AppOpsValue.ALLOW,
            )
            log(TAG) { "writeServicesViaShell(): ACCESS_RESTRICTED_SETTINGS=allow OK" }
        } catch (e: Exception) {
            log(TAG, WARN) { "writeServicesViaShell(): ACCESS_RESTRICTED_SETTINGS set failed: ${e.asLog()}" }
        }

        val cmd = AcsActivator.enabledServicesPutCmd(AcsActivator.flattenServices(services))
        val result = try {
            shellOps.execute(ShellOpsCmd(cmd), mode)
        } catch (e: Exception) {
            log(TAG, WARN) { "writeServicesViaShell(): shell write failed: ${e.asLog()}" }
            return false
        }
        if (!result.isSuccess) {
            log(TAG, WARN) { "writeServicesViaShell(): shell write unsuccessful: $result" }
            return false
        }

        val after = getAutomationServices()
        val persisted = AcsActivator.writeMatchesIntent(intent = services, actual = after)
        log(TAG, INFO) { "writeServicesViaShell($services) -> $after (mode=$mode, persisted=$persisted)" }
        return persisted
    }

    /**
     * Strategy-aware write used for disable / re-toggle, where binding is NOT the success signal.
     * On a build flagged direct-write-unreliable we never direct-write (it can wipe third-party
     * services); if no shell is available there we skip rather than fall back to a destructive write.
     *
     * @return true if a write was actually performed.
     */
    private suspend fun writeServices(services: Set<ComponentName>): Boolean {
        val mode = shellMode()
        return when (AcsActivator.writeStrategy(acsWriteReliability.shouldAvoidDirectWrite(), mode != null)) {
            AcsActivator.WriteStrategy.DIRECT -> {
                writeServicesDirect(services)
                true
            }

            AcsActivator.WriteStrategy.SHELL -> {
                writeServicesViaShell(services, mode!!)
                true
            }

            AcsActivator.WriteStrategy.SKIP -> {
                log(TAG, WARN) {
                    "writeServices(): avoid-direct build with no shell; skipping write to avoid destructive direct mutation"
                }
                false
            }
        }
    }

    private suspend fun waitForServiceBound(timeoutMs: Long): AutomationServiceHandle? =
        withTimeoutOrNull(timeoutMs) {
            while (currentCoroutineContext().isActive) {
                serviceHolder.value?.let { return@withTimeoutOrNull it }
                log(TAG, VERBOSE) { "waitForServiceBound(): Waiting for service to start" }
                delay(500)
            }
            null
        }

    private val acsActivator = AcsActivator(object : AcsActivator.Io {
        override suspend fun shellMode(): ShellOps.Mode? = this@AutomationManager.shellMode()
        override suspend fun isAvoidDirectWrite(): Boolean = acsWriteReliability.shouldAvoidDirectWrite()
        override suspend fun markDirectWriteUnreliable() = acsWriteReliability.markDirectWriteUnreliable()
        override suspend fun writeDirect(services: Set<ComponentName>) = writeServicesDirect(services)
        override suspend fun writeShell(services: Set<ComponentName>, mode: ShellOps.Mode) =
            writeServicesViaShell(services, mode)

        override suspend fun awaitBound(timeoutMs: Long) = waitForServiceBound(timeoutMs)
    })

    suspend fun isServiceEnabled(): Boolean = getAutomationServices().contains(ourServiceComp)

    suspend fun isShortcutOrButtonEnabled(): Boolean {
        val shortcutTarget = runCatching {
            settingsProvider.get<String>(
                SystemSettingsProvider.Type.SECURE,
                "accessibility_shortcut_target_service"
            )
        }.getOrNull()

        val buttonTarget = runCatching {
            settingsProvider.get<String>(
                SystemSettingsProvider.Type.SECURE,
                "accessibility_button_targets"
            )
        }.getOrNull()

        return parseAccessibilityTargets(shortcutTarget, buttonTarget).contains(ourServiceComp)
    }

    fun isServiceLaunched() = serviceHolder.value != null

    suspend fun canSelfEnable() = Permission.WRITE_SECURE_SETTINGS.isGranted(context)

    private suspend fun startService(): AutomationServiceHandle {
        log(TAG, VERBOSE) { "startService()" }
        serviceHolder.value?.let {
            log(TAG) { "startService(): ACS is already running" }
            return it
        }

        if (!setupHelper.hasSecureSettings()) {
            log(TAG, WARN) { "startService(): Service is not running and we don't have secure settings access." }
            throw AutomationNotEnabledException()
        }

        log(TAG) { "startService(): Starting service..." }

        val currentServices = getAutomationServices()
        log(TAG) { "startService(): Before writing ACS settings: $currentServices" }

        // Snapshot of the full desired list, preserving any third-party services already enabled.
        val intended = currentServices + ourServiceComp

        if (currentServices.contains(ourServiceComp)) {
            log(TAG, WARN) { "startService(): Service isn't running but we are already enabled? Let's re-toggle" }
            if (writeServices(currentServices - ourServiceComp)) {
                // Give the system some time to process the toggle-off
                delay(3000)
            }
        }

        val service = acsActivator.enable(intended)
        if (service == null) {
            if (acsWriteReliability.shouldAvoidDirectWrite() && shellMode() == null) {
                log(TAG, WARN) { "startService(): Direct write disabled for this build and no privileged shell available." }
                throw AutomationNotEnabledException()
            }
            throw AutomationNotRunningException()
        }

        log(TAG, INFO) { "startService(): Service started!" }
        return service
    }

    private suspend fun stopService() {
        log(TAG, VERBOSE) { "stopService()" }
        if (!setupHelper.hasSecureSettings()) {
            throw IllegalStateException("stopService(): Trying to stop service but secure settings permission isn't available")
        }

        val currentServices = getAutomationServices()

        val newServices = currentServices - ourServiceComp
        if (currentServices == newServices) {
            log(TAG, WARN) { "stopService(): We were not part of the active service components: $currentServices" }
        }

        if (!writeServices(newServices)) {
            // avoid-direct build with no shell: skipping is safer than a destructive direct write.
            // Our service stays enabled; don't wait for an unbind that won't happen.
            log(TAG, WARN) { "stopService(): Disable write skipped, leaving service enabled" }
            return
        }

        log(TAG, VERBOSE) { "stopService(): Waiting for service to stop" }
        val stopped = withTimeoutOrNull(STOP_TIMEOUT_MS) {
            serviceHolder.filter { it == null }.first()
            true
        } != null
        if (stopped) {
            log(TAG, INFO) { "stopService(): Service stopped!" }
        } else {
            // A failed disable write (e.g. shell-first build with the shell unavailable) must not hang
            // the awaitClose/runBlocking forever waiting for an unbind that won't happen.
            log(TAG, WARN) { "stopService(): Timed out waiting for the service to unbind" }
        }
    }

    private val serviceLauncher = callbackFlow {
        if (settings.hasAcsConsent.value() != true) {
            log(TAG, WARN) { "serviceLauncher: No user consent for ACS!" }
            throw AutomationNoConsentException()
        }

        val serviceWasRunning = isServiceLaunched()
        log(TAG) { "serviceLauncher: serviceWasRunning=$serviceWasRunning" }

        val canToggle = setupHelper.hasSecureSettings()
        log(TAG) { "serviceLauncher: canToggle=$canToggle" }

        // startService() owns its own bounded waits (direct + optional shell fallback) and throws on
        // failure, so it must NOT be wrapped in an outer timeout that could cancel the fallback midway.
        val service = if (canToggle) {
            startService()
        } else {
            serviceHolder.value ?: throw AutomationNotRunningException()
        }

        val wrapper = ServiceWrapper(service = service)

        send(wrapper)

        log(TAG) { "serviceLauncher: Service provided, waiting for close..." }
        awaitClose {
            log(TAG) { "serviceLauncher: Closing..." }
            if (canToggle) {
                log(TAG) { "serviceLauncher: Can be launched on demand, stopping it after use..." }
                runBlocking { stopService() }
            }
        }
    }
        .setupCommonEventHandlers(TAG) { "serviceLauncher" }

    private val serviceResource = SharedResource(TAG, appScope, serviceLauncher)

    override suspend fun submit(task: AutomationTask): AutomationTask.Result {
        log(TAG) { "submit(): $task" }
        return serviceResource.useRes { it.submit(task) }
    }

    private val currentTaskHolder = MutableStateFlow<AutomationTask?>(null)
    val currentTask: Flow<AutomationTask?> = currentTaskHolder

    internal fun setCurrentTask(task: AutomationTask?) {
        log(TAG, VERBOSE) { "setCurrentTask($task)" }
        currentTaskHolder.value = task
    }

    fun cancelTask(): Boolean {
        return serviceHolder.value?.cancelTask() ?: false
    }

    data class ServiceWrapper(
        private val service: AutomationServiceHandle,
    ) {
        @Suppress("UNCHECKED_CAST")
        suspend fun <R> submit(task: AutomationTask): R = service.submit(task) as R
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds abstract fun submitter(manager: AutomationManager): AutomationSubmitter
    }

    companion object {
        val TAG: String = logTag("Automation", "Manager")

        private const val STOP_TIMEOUT_MS = 10 * 1000L

        internal fun parseAccessibilityTargets(vararg settings: String?): Set<ComponentName> =
            settings.filterNotNull()
                .flatMap { it.split(":") }
                .filter { it.isNotBlank() }
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .toSet()
    }
}
