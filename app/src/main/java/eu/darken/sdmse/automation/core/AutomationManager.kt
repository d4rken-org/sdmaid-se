package eu.darken.sdmse.automation.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.animation.AnimationTool
import eu.darken.sdmse.automation.core.errors.AutomationNoConsentException
import eu.darken.sdmse.automation.core.errors.AutomationNotEnabledException
import eu.darken.sdmse.automation.core.errors.AutomationNotRunningException
import eu.darken.sdmse.common.SystemSettingsProvider
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
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.useRes
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
    private val animationTool: AnimationTool,
) {

    init {
        appScope.launch {
            try {
                animationTool.restorePendingState()
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to restore pending animation state on init: ${e.asLog()}" }
            }
        }
    }

    private val serviceHolder = MutableStateFlow(AutomationService.instance)
    val currentService: Flow<AutomationService?> = serviceHolder

    internal fun setCurrentService(service: AutomationService?) {
        log(TAG) { "setCurrentService($service)" }
        serviceHolder.value = service
    }

    val useAcs: Flow<Boolean> = combine(
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

    private suspend fun setAutomationServices(services: Set<ComponentName>) {
        settingsProvider.put(
            SystemSettingsProvider.Type.SECURE,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services.joinToString(":") { it.flattenToString() }
        )
        val after = getAutomationServices()
        log(TAG) { "setAutomationServices($services) -> $after" }
    }

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

    private suspend fun startService(): AutomationService {
        log(TAG, VERBOSE) { "startService()" }
        var service = serviceHolder.value

        if (service != null) {
            log(TAG) { "startService(): ACS is already running" }
            return service
        }

        if (!setupHelper.hasSecureSettings()) {
            log(TAG, WARN) { "startService(): Service is not running and we don't have secure settings access." }
            throw AutomationNotEnabledException()
        }

        log(TAG) { "startService(): Starting service..." }

        val currentServices = getAutomationServices()
        log(TAG) { "startService(): Before writing ACS settings: $currentServices" }

        if (currentServices.contains(ourServiceComp)) {
            log(TAG, WARN) { "startService(): Service isn't running but we are already enabled? Let's re-toggle" }
            setAutomationServices(currentServices.minus(ourServiceComp))

            // Give the system some time
            delay(3000)

            val afterToggleOff = getAutomationServices()
            if (afterToggleOff.contains(ourServiceComp)) throw IllegalStateException("Failed to remove our ACS service")

            setAutomationServices(afterToggleOff.plus(ourServiceComp))
        } else {
            val newAcsValue = currentServices.plus(ourServiceComp)
            setAutomationServices(newAcsValue)
        }

        while (currentCoroutineContext().isActive) {
            service = serviceHolder.value
            if (service != null) break
            log(TAG, VERBOSE) { "startService(): Waiting for service to start" }
            delay(500)
        }

        log(TAG, INFO) { "startService(): Service started!" }
        return service!!
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

        setAutomationServices(newServices)

        log(TAG, VERBOSE) { "stopService(): Waiting for service to stop" }
        serviceHolder.filter { it == null }.first()
        log(TAG, INFO) { "stopService(): Service stopped!" }
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

        val service = if (canToggle) {
            withTimeoutOrNull(10 * 1000L) { startService() }
        } else {
            serviceHolder.value
        }

        if (service == null) throw AutomationNotRunningException()

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

    suspend fun submit(task: AutomationTask): AutomationTask.Result {
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
        private val service: AutomationService,
    ) {
        @Suppress("UNCHECKED_CAST")
        suspend fun <R> submit(task: AutomationTask): R = service.submit(task) as R
    }

    companion object {
        val TAG: String = logTag("Automation", "Manager")

        internal fun parseAccessibilityTargets(vararg settings: String?): Set<ComponentName> =
            settings.filterNotNull()
                .flatMap { it.split(":") }
                .filter { it.isNotBlank() }
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .toSet()
    }
}