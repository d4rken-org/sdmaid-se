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
import androidx.core.view.isVisible
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

    private fun checkLaunch(): Boolean = if (!isValidAndroidEntryPoint()) {
        log(TAG, ERROR) { "Invalid launch: Launched by foreign application: $application" }
        // stopSelf()/disableSelf() doesn't work in onCreate()
        disableSelf()
        false
    } else {
        true
    }

    private lateinit var controlView: AutomationControlView

    override fun onCreate() {
        log(TAG) { "onCreate(application=$application)" }
        Bugs.leaveBreadCrumb("Automation service launched")

        try {
            super.onCreate()
        } catch (e: IllegalStateException) {
            if (checkLaunch()) throw e else return
        }

        if (mightBeRestrictedDueToSideload() && !generalSettings.hasPassedAppOpsRestrictions.valueBlocking) {
            log(TAG, INFO) { "We are not restricted by app ops." }
            generalSettings.hasPassedAppOpsRestrictions.valueBlocking = true
        }

        controlView = AutomationControlView(ContextThemeWrapper(this@AutomationService, R.style.AppTheme)).apply {
            setCancelListener {
                serviceScope.launch {
                    changeOptions { it.copy(showOverlay = false) }
                }
                currentTaskJob?.cancel(cause = UserCancelledAutomationException())
            }
        }

        serviceScope.launch {
            delay(2000)
            automationSetupModule.refresh()
        }

        progress
            .onEach { pd -> withContext(dispatcher.Main) { controlView.setProgress(pd) } }
            .launchIn(serviceScope)

        screenState.state
            .map { it.isScreenAvailable }
            .distinctUntilChanged()
            .onEach { available ->
                log(TAG, INFO) { "Updating controllview isVisible=$available" }
                withContext(dispatcher.Main) { controlView.isVisible = available }
            }
            .launchIn(serviceScope)
    }

    override fun onInterrupt() {
        log(TAG) { "onInterrupt()" }
    }

    override fun onServiceConnected() {
        log(TAG) { "onServiceConnected()" }
        if (!checkLaunch()) return

        instance = this
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
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        log(TAG) { "onDestroy()" }
        Bugs.leaveBreadCrumb("Automation service destroyed")

        if (!checkLaunch()) return

        serviceScope.cancel()
        super.onDestroy()
    }

    override suspend fun windowRoot(): AccessibilityNodeInfo? = suspendCancellableCoroutine {
        val rootNode: AccessibilityNodeInfo? = rootInActiveWindow
        log(TAG, VERBOSE) { "Providing windowRoot: ${rootNode?.toStringShort()}" }
        it.resume(rootNode)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!checkLaunch()) return

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

    private val controlLp: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        gravity = Gravity.BOTTOM
    }

    private var currentOptions: AutomationHost.Options = AutomationHost.Options()
    private val optionsLock = Mutex()

    override suspend fun changeOptions(
        action: (AutomationHost.Options) -> AutomationHost.Options
    ) = optionsLock.withLock {
        val newOptions = action(currentOptions)
        if (Bugs.isTrace) {
            log(TAG, VERBOSE) { "changeOptions(): Old options: $currentOptions" }
            log(TAG, VERBOSE) { "changeOptions(): New options: $newOptions" }
        }

        serviceInfo = newOptions.accessibilityServiceInfo

        controlView.setTitle(newOptions.controlPanelTitle, newOptions.controlPanelSubtitle)

        controlLp.apply {
            flags = if (newOptions.passthrough) {
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
        }
        val isControlViewAdded = controlView.windowToken != null && controlView.isAttachedToWindow
        when {
            newOptions.showOverlay && isControlViewAdded -> try {
                log(TAG) { "changeOptions(): Updating controlview" }
                withContext(dispatcher.Main) {
                    windowManager.updateViewLayout(controlView, controlLp)
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "changeOptions(): Failed to update control view: ${e.asLog()}" }
            }

            newOptions.showOverlay && !isControlViewAdded -> try {
                log(TAG, INFO) { "changeOptions(): Adding controlview" }
                withContext(dispatcher.Main) {
                    windowManager.addView(controlView, controlLp)
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "changeOptions(): Failed to add control view to window: ${e.asLog()}" }
            }

            !newOptions.showOverlay && isControlViewAdded -> try {
                log(TAG, INFO) { "changeOptions(): Removing controlview: $controlView" }
                val isDetached = CompletableDeferred<Unit>()
                withContext(dispatcher.Main) {
                    controlView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewDetachedFromWindow(v: View) {
                            v.removeOnAttachStateChangeListener(this)
                            log(TAG) { "changeOptions(): controlview removed: $v" }
                            isDetached.complete(Unit)
                        }

                        override fun onViewAttachedToWindow(v: View) {}
                    })
                    windowManager.removeView(controlView)
                }
                isDetached.await()
            } catch (e: Exception) {
                log(TAG, WARN) { "changeOptions(): Failed to remove controlview, not added? $controlView" }
            }
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

    private var currentTaskJob: Job? = null
    private val taskLock = Mutex()

    suspend fun submit(task: AutomationTask): AutomationTask.Result = taskLock.withLock {
        log(TAG) { "submit(): $task" }

        if (generalSettings.hasAcsConsent.valueBlocking != true) {
            log(TAG, WARN) { "Missing consent for accessibility service, skipping task." }
            throw AutomationNoConsentException()
        }

        updateProgress { Progress.Data() }
        val deferred = serviceScope.async {
            try {
                automationProcessor.process(task)
            } finally {
                updateProgress { null }
            }
        }
        currentTaskJob = deferred
        log(TAG) { "submit(): ...waiting for result" }
        deferred.await()
    }

    override val service: AccessibilityService get() = this

    companion object {
        val TAG: String = logTag("Automation", "Service")
        var instance: AutomationService? = null
    }
}