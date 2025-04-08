package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.Keep
import androidx.appcompat.view.ContextThemeWrapper
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.automation.core.common.toStringShort
import eu.darken.sdmse.automation.core.errors.AutomationNoConsentException
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private val automationEvents = MutableSharedFlow<AccessibilityEvent>()
    override val events: Flow<AccessibilityEvent> = automationEvents

    private val hostState = MutableStateFlow(AutomationHost.State())
    override val state = hostState

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private var controlView: AutomationControlView? = null

    override val service: AccessibilityService get() = this

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
                    val desiredVis = currentOptions.showOverlay
                    val desiredPass = currentOptions.passthrough
                    log(TAG) { "ScreenState: desiredVis=$desiredPass, desiredPass=$desiredPass" }
                    if (!available) {
                        log(TAG, INFO) { "ScreenState: Controlview should be hidden" }
                        isOverlayBlocked = true
                        updateOverlay(visible = false, passthrough = true)
                    } else if (automationProcessor.hasTask) {
                        log(TAG, INFO) { "ScreenState: Controlview can be visible, screen available" }
                        isOverlayBlocked = false
                        updateOverlay(visible = desiredVis, passthrough = desiredPass)
                    }
                }
            }
            .launchIn(serviceScope)

        currentTask
            .map { it?.first }
            .onEach { automationManager.setCurrentTask(it) }
            .launchIn(serviceScope)
    }

    override fun onInterrupt() {
        log(TAG) { "onInterrupt()" }
    }

    override fun onServiceConnected() {
        log(TAG) { "onServiceConnected()" }
        instance = this
        automationManager.setCurrentService(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

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

    override suspend fun windowRoot(): AccessibilityNodeInfo? = suspendCancellableCoroutine {
        val rootNode: AccessibilityNodeInfo? = rootInActiveWindow
        log(TAG, VERBOSE) { "Providing windowRoot: ${rootNode?.toStringShort()}" }
        it.resume(rootNode)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (generalSettings.hasAcsConsent.valueBlocking != true) {
            log(TAG, WARN) { "Missing consent for accessibility service, skipping event." }
            return
        }

        if (!automationProcessor.hasTask) return

        if (Bugs.isTrace) log(TAG, VERBOSE) { "onAccessibilityEvent(eventType=${event.eventType})" }

        val eventCopy = if (hasApiLevel(30)) {
            @Suppress("NewApi")
            AccessibilityEvent(event)
        } else {
            try {
                @Suppress("DEPRECATION")
                AccessibilityEvent.obtain(event)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to obtain accessibility event copy $event" }
                return
            }
        }

        // TODO use a queue here?
        serviceScope.launch {
            log(TAG, VERBOSE) { "Providing: $eventCopy" }
            automationEvents.emit(eventCopy)
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
        log(TAG) { "setOverlayVisibility(visible=$visible, passthrough=$passthrough)" }
        val cv = controlView
        if (cv == null) {
            log(TAG, WARN) { "setOverlayVisibility(...) controlView was null" }
            return@withContext
        }

        cv.alpha = if (visible) 1f else 0f

        overlayParams.flags = if (passthrough) {
            overlayParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            overlayParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        windowManager.updateViewLayout(cv, overlayParams)
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

        if (!isOverlayBlocked) {
            updateOverlay(visible = newOptions.showOverlay, passthrough = newOptions.passthrough)
        }

        delay(80) // approx ~3 frames
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
                windowManager.addView(this, overlayParams)
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