package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.Keep
import androidx.appcompat.view.ContextThemeWrapper
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.pkgId
import eu.darken.sdmse.automation.core.common.toNodeInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.automation.core.errors.AutomationNoConsentException
import eu.darken.sdmse.automation.core.errors.AutomationOverlayException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.automation.ui.AutomationControlView
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.isValidAndroidEntryPoint
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.automation.mightBeRestrictedDueToSideload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

@Keep
@AndroidEntryPoint
class AutomationService : AccessibilityService(), AutomationHost, Progress.Host, Progress.Client {

    @Inject lateinit var dispatcher: DispatcherProvider
    private val serviceScope: CoroutineScope by lazy { CoroutineScope(dispatcher.Default + SupervisorJob()) }
    override val scope: CoroutineScope get() = serviceScope

    @Inject lateinit var automationProcessorFactory: AutomationProcessor.Factory
    private val automationProcessor: AutomationProcessor by lazy { automationProcessorFactory.create(this) }

    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var automationManager: AutomationManager
    @Inject lateinit var automationSetupModule: AutomationSetupModule
    @Inject lateinit var screenState: ScreenState

    private lateinit var windowManager: WindowManager

    private val automationEvents = MutableSharedFlow<Snapshot>(
        extraBufferCapacity = 10,
    )
    override val events: Flow<AutomationEvent> = automationEvents

    private val hostState = MutableStateFlow(AutomationHost.State())
    override val state = hostState

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private var controlView: AutomationControlView? = null

    override val service: AccessibilityService get() = this

    private val screenLogger = MutableSharedFlow<Snapshot>(
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    data class Snapshot(
        override val id: UUID = UUID.randomUUID(),
        val event: AccessibilityEvent,
    ) : AutomationEvent {
        override val pkgId: Pkg.Id?
            get() = event.pkgId
        override val eventType: Int
            get() = event.eventType
    }

    override fun onCreate() {
        log(TAG) { "onCreate(application=$application)" }
        Bugs.leaveBreadCrumb("Automation service launched")

        try {
            super.onCreate()
        } catch (e: IllegalStateException) {
            if (isValidAndroidEntryPoint()) throw e

            log(TAG, ERROR) { "Invalid launch: Launched by foreign application: $application" }
            // stopSelf()/disableSelf() doesn't work in onCreate()
            disableSelf()
            return
        }

        if (mightBeRestrictedDueToSideload() && !generalSettings.hasPassedAppOpsRestrictions.valueBlocking) {
            log(TAG, INFO) { "We are not restricted by app ops." }
            generalSettings.hasPassedAppOpsRestrictions.valueBlocking = true
        }

        serviceScope.launch {
            delay(2000)
            automationSetupModule.refresh()
        }

        progress
            .onEach { pd -> withContext(dispatcher.Main) { controlView?.setProgress(pd) } }
            .launchIn(serviceScope)

        screenState.state
            .map { it.isScreenAvailable }
            .distinctUntilChanged()
            .onEach { available ->
                optionsLock.withLock {
                    val hasTask = automationProcessor.hasTask
                    val opts = currentOptions
                    log(TAG) { "ScreenState: available=$available, hasTask=$hasTask, options=$opts" }

                    if (isOverlayBlocked != !available) log(TAG, WARN) { "ScreenState: isOverlayBlocked=${!available}" }
                    isOverlayBlocked = !available

                    if (!hasTask) {
                        log(TAG, INFO) { "ScreenState: No task active, not changing overlay state" }
                        return@withLock
                    }

                    if (available) {
                        log(TAG, INFO) { "ScreenState: Restoring desired overlay state" }
                        updateOverlay(visible = opts.showOverlay, passthrough = opts.passthrough)
                    } else {
                        log(TAG, INFO) { "ScreenState: Hiding overlay if visible" }
                        updateOverlay(visible = false, passthrough = true)
                    }
                }
            }
            .launchIn(serviceScope)

        currentTask
            .map { it?.first }
            .onEach { automationManager.setCurrentTask(it) }
            .launchIn(serviceScope)

        screenLogger
            .filter { Bugs.isDebug }
            .onEach { (id, event) ->
                log(TAG, VERBOSE) { "ACS-DEBUG -- $id -- START -- $event" }
                withTimeout(3_000) { windowRoot() }
                    ?.crawl()
                    ?.forEach { log(TAG, VERBOSE) { "ACS-DEBUG: ${it.infoShort}" } }
                log(TAG, VERBOSE) { "ACS-DEBUG -- $id -- STOP -- ------------------------------" }
            }
            .retry {
                log(TAG, ERROR) { "Failed to log screen: ${it.asLog()}" }
                true
            }
            .launchIn(serviceScope)
    }

    override fun onInterrupt() {
        log(TAG) { "onInterrupt()" }
    }

    override fun onServiceConnected() {
        log(TAG) { "onServiceConnected()" }
        if (!this::automationManager.isInitialized) {
            log(TAG, WARN) { "onServiceConnected() called before injection completed, ignoring." }
            return
        }
        instance = this
        automationManager.setCurrentService(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (generalSettings.hasAcsConsent.valueBlocking != true) {
            log(TAG, WARN) { "Missing consent for accessibility service, stopping service." }
            // disableSelf() does not work if called within `onCreate()`
            disableSelf()
            return
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        log(TAG) { "onUnbind(intent=$intent)" }
        instance = null
        automationManager.setCurrentService(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        log(TAG) { "onDestroy()" }
        Bugs.leaveBreadCrumb("Automation service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    override suspend fun windowRoot(): ACSNodeInfo? = suspendCancellableCoroutine {
        val rootNode: ACSNodeInfo? = rootInActiveWindow?.toNodeInfo()
        log(TAG, VERBOSE) { "Providing windowRoot: $rootNode" }
        it.resume(rootNode)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (generalSettings.hasAcsConsent.valueBlocking != true) {
            log(TAG, WARN) { "Missing consent for accessibility service, skipping event." }
            return
        }

        if (!automationProcessor.hasTask) return

        val eventCopy = if (hasApiLevel(30)) {
            @Suppress("NewApi")
            AccessibilityEvent(event)
        } else {
            try {
                @Suppress("DEPRECATION")
                AccessibilityEvent.obtain(event)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to obtain accessibility event copy $event: ${e.asLog()}" }
                return
            }
        }

        val snapshot = Snapshot(event = eventCopy)

        screenLogger.tryEmit(snapshot)

        serviceScope.launch {
            log(TAG, VERBOSE) { "Providing: $snapshot" }
            automationEvents.emit(snapshot)
        }
    }

    private val overlayParams: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        gravity = Gravity.BOTTOM
    }

    private var currentOptions: AutomationHost.Options = AutomationHost.Options()
    private val optionsLock = Mutex()

    private var isOverlayBlocked: Boolean = false

    private suspend fun updateOverlay(
        visible: Boolean,
        passthrough: Boolean,
    ) = withContext(dispatcher.Main) {
        log(TAG) { "updateOverlay(visible=$visible, passthrough=$passthrough, isOverlayBlocked=$isOverlayBlocked)" }
        val cv = controlView
        if (cv == null) {
            log(TAG, WARN) { "updateOverlay(...) controlView was null" }
            return@withContext
        }

        try {
            cv.alpha = if (visible) 1f else 0f
            log(TAG, INFO) { "Updated controlView alpha to ${cv.alpha}" }

            overlayParams.flags = if (passthrough) {
                overlayParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                overlayParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            log(TAG, INFO) { "Updating view layout with flags=${overlayParams.flags}" }
            windowManager.updateViewLayout(cv, overlayParams)
            log(TAG, INFO) { "View layout updated successfully" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to update overlay: ${e.asLog()}" }
        }
    }

    override suspend fun changeOptions(
        action: (AutomationHost.Options) -> AutomationHost.Options
    ) = optionsLock.withLock {
        val newOptions = action(currentOptions)

        log(TAG, VERBOSE) { "changeOptions(): Old options: $currentOptions" }
        log(TAG, VERBOSE) { "changeOptions(): New options: $newOptions" }

        serviceInfo = newOptions.accessibilityServiceInfo

        withContext(dispatcher.Main) {
            controlView?.setTitle(newOptions.controlPanelTitle, newOptions.controlPanelSubtitle)
        }

        log(TAG) { "changeOptions(): isOverlayBlocked=$isOverlayBlocked" }
        if (!isOverlayBlocked) {
            updateOverlay(visible = newOptions.showOverlay, passthrough = newOptions.passthrough)
        }

        delay(200)
        log(TAG, VERBOSE) { "changeOptions(): Updating new hostState" }
        currentOptions = newOptions
        hostState.update {
            it.copy(
                passthrough = newOptions.passthrough,
                hasOverlay = newOptions.showOverlay
            )
        }
        log(TAG, VERBOSE) { "changeOptions(): New options applied." }
    }

    private var currentTask = MutableStateFlow<Pair<AutomationTask, Job>?>(null)
    private val taskLock = Mutex()

    suspend fun submit(task: AutomationTask): AutomationTask.Result = taskLock.withLock {
        val id = task.hashCode()
        log(TAG, INFO) { "submit($id): $task" }

        if (generalSettings.hasAcsConsent.valueBlocking != true) {
            log(TAG, WARN) { "submit($id): Missing consent for accessibility service, skipping task." }
            throw AutomationNoConsentException()
        }

        controlView = withContext(dispatcher.Main) {
            AutomationControlView(ContextThemeWrapper(this@AutomationService, R.style.AppTheme)).apply {
                setCancelListener {
                    serviceScope.launch { changeOptions { it.copy(showOverlay = false) } }
                    currentTask.value?.second?.cancel(cause = UserCancelledAutomationException())
                }
                alpha = 0f
                log(TAG) { "submit($id): Adding controlView" }
                try {
                    windowManager.addView(this, overlayParams)
                } catch (e: WindowManager.BadTokenException) {
                    log(TAG, ERROR) { "submit($id): Failed to add controlView: ${e.asLog()}" }
                    throw AutomationOverlayException(e)
                }
            }
        }

        changeOptions { AutomationHost.Options() }
        updateProgress { Progress.Data() }

        val deferred = serviceScope.async {
            try {
                log(TAG) { "submit($id): Processing task..." }
                automationProcessor.process(task).also { log(TAG, INFO) { "submit($id): ... task processed." } }
            } catch (e: Exception) {
                log(TAG) { "submit($id) task ended with exception: ${e.asLog()}" }
                throw e
            } finally {
                withContext(NonCancellable) {
                    updateProgress { null }
                    optionsLock.withLock {
                        val isDetached = CompletableDeferred<Unit>()

                        controlView!!
                            .apply {
                                val listener = object : View.OnAttachStateChangeListener {
                                    override fun onViewDetachedFromWindow(v: View) {
                                        v.removeOnAttachStateChangeListener(this)
                                        log(TAG) { "submit($id): controlView removed: $v" }
                                        isDetached.complete(Unit)
                                    }

                                    override fun onViewAttachedToWindow(v: View) {}
                                }
                                withContext(dispatcher.Main) { addOnAttachStateChangeListener(listener) }
                            }
                            .also {
                                log(TAG) { "submit($id): Removing controlView: $it" }
                                withContext(dispatcher.Main) { windowManager.removeView(it) }
                            }

                        isDetached.await()
                        controlView = null
                    }
                    currentTask.value = null
                    log(TAG) { "submit($id): ...task complete" }
                }
            }
        }
        currentTask.value = task to deferred
        automationManager.setCurrentTask(task)
        log(TAG) { "submit($id): ...waiting for result" }
        deferred.await().also { log(TAG) { "submit($id): Result available: $it" } }
    }

    fun cancelTask(): Boolean {
        log(TAG) { "cancelTask()" }
        val (task, job) = currentTask.value ?: return false
        log(TAG, INFO) { "cancelTask(): Canceling $task" }
        job.cancel()
        return true
    }

    companion object {
        val TAG: String = logTag("Automation", "Service")
        var instance: AutomationService? = null
    }
}