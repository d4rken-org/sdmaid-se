package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.Keep
import androidx.appcompat.view.ContextThemeWrapper
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.automation.core.common.AutomationException
import eu.darken.sdmse.automation.core.common.getRoot
import eu.darken.sdmse.automation.core.errors.AutomationNoConsentException
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
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.automation.mightBeRestrictedDueToSideload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.coroutines.resume

@Keep
@AndroidEntryPoint
class AutomationService : AccessibilityService(), AutomationHost, Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    @Inject lateinit var dispatcher: DispatcherProvider
    private val serviceScope: CoroutineScope by lazy { CoroutineScope(dispatcher.Default + SupervisorJob()) }
    override val scope: CoroutineScope get() = serviceScope

    @Inject lateinit var automationProcessorFactory: AutomationProcessor.Factory
    private val automationProcessor: AutomationProcessor by lazy { automationProcessorFactory.create(this) }

    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var automationSetupModule: AutomationSetupModule

    private var currentOptions = AutomationHost.Options()
    private lateinit var windowManager: WindowManager
    private val mainThread = Handler(Looper.getMainLooper())

    private val automationEvents = MutableSharedFlow<AccessibilityEvent>()
    override val events: Flow<AccessibilityEvent> = automationEvents

    private var controlView: AutomationControlView? = null

    override fun onCreate() {
        log(TAG) { "onCreate(application=$application)" }
        super.onCreate()

        Bugs.leaveBreadCrumb("Automation service launched")

        if (mightBeRestrictedDueToSideload() && !generalSettings.hasPassedAppOpsRestrictions.valueBlocking) {
            log(TAG, INFO) { "We are not restricted by app ops." }
            generalSettings.hasPassedAppOpsRestrictions.valueBlocking = true
        }

        serviceScope.launch {
            delay(2000)
            automationSetupModule.refresh()
        }

        progress
            .mapLatest { progressData ->
                mainThread.post {
                    val acv = controlView
                    when {
                        progressData == null && acv != null -> {
                            log(TAG) { "Removing controlview: $acv" }
                            try {
                                windowManager.removeView(acv)
                            } catch (e: Exception) {
                                log(TAG, WARN) { "Failed to remove controlview, not added? $acv" }
                            }
                            controlView = null
                        }

                        progressData != null && acv == null -> {
                            log(TAG) { "Adding controlview" }
                            val view = AutomationControlView(ContextThemeWrapper(this, R.style.AppTheme))
                            log(TAG) { "Adding new controlview: $view" }
                            view.setCancelListener {
                                view.showOverlay(false)
                                currentTaskJob?.cancel()
                            }

                            try {
                                windowManager.addView(view, controlLp)
                                controlView = view
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Failed to add control view to window: ${e.asLog()}" }
                            }
                        }

                        acv != null -> {
                            log(TAG, VERBOSE) { "Updating control view" }
                            log(TAG, VERBOSE) { "Updating progress $progress" }
                            acv.setProgress(progressData)
                        }

                        else -> {
                            log(TAG, VERBOSE) { "ControlView is $acv and progress is $progressData" }
                        }
                    }
                }
            }
            .launchIn(serviceScope)
    }

    override fun onInterrupt() {
        log(TAG) { "onInterrupt()" }
    }

    override fun onServiceConnected() {
        log(TAG) { "onServiceConnected()" }
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
        serviceScope.cancel()
        super.onDestroy()
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
                log(TAG, ERROR) { "Failed to obtain accessibility event copy $event" }
                return
            }
        }

        if (Bugs.isDebug) log(TAG, VERBOSE) { "New automation event: $eventCopy" }

        serviceScope.launch {
            try {
                eventCopy.source
                    ?.getRoot(maxNesting = Int.MAX_VALUE)
                    ?.let {
                        fallbackMutex.withLock {
                            if (!hasApiLevel(30)) {
                                @Suppress("DEPRECATION")
                                fallbackRoot?.recycle()
                            }
                            fallbackRoot = it
                        }
                    }
                    .also { log(TAG, VERBOSE) { "Fallback root was $fallbackRoot, now is $it" } }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to get fallbackRoot from $event: $e" }
            }
        }

        serviceScope.launch {
            // If we need fallbackRoot, don't race it
            delay(50)
            log(TAG, VERBOSE) { "Providing event: $eventCopy" }
            automationEvents.emit(eventCopy)
        }
    }

    private val fallbackMutex = Mutex()
    private var fallbackRoot: AccessibilityNodeInfo? = null

    override suspend fun windowRoot(): AccessibilityNodeInfo = suspendCancellableCoroutine {
        val maybeRootNode: AccessibilityNodeInfo? = rootInActiveWindow ?: fallbackRoot?.also {
            log(TAG, WARN) { "Using fallback rootNode: $it" }
        }

        log(TAG, VERBOSE) { "Providing window root: $maybeRootNode" }
        it.resume(maybeRootNode ?: throw AutomationException("Root node is currently null"))
    }

    private val controlLp: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        gravity = Gravity.BOTTOM
    }

    override suspend fun changeOptions(action: (AutomationHost.Options) -> AutomationHost.Options) {
        val newOptions = action(currentOptions)
        currentOptions = newOptions

        mainThread.post {
            controlView?.let { acv ->
                controlLp.gravity = newOptions.panelGravity
                windowManager.updateViewLayout(acv, controlLp)

                if (newOptions.showOverlay) {
                    controlLp.height = WindowManager.LayoutParams.MATCH_PARENT
                } else {
                    controlLp.height = WindowManager.LayoutParams.WRAP_CONTENT
                }

                windowManager.updateViewLayout(acv, controlLp)
                acv.showOverlay(newOptions.showOverlay)
                acv.setTitle(newOptions.controlPanelTitle, newOptions.controlPanelSubtitle)
            }
        }
    }

    private var currentTaskJob: Job? = null
    private val taskLock = Mutex()

    suspend fun submit(task: AutomationTask): AutomationTask.Result = taskLock.withLock {
        log(TAG) { "submit(): $task" }

        if (generalSettings.hasAcsConsent.valueBlocking != true) {
            log(TAG, WARN) { "Missing consent for accessibility service, skipping task." }
            throw AutomationNoConsentException()
        }

        updateProgress { Progress.DEFAULT_STATE }
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