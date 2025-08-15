package eu.darken.sdmse.common.debug.memory

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Debug
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("DEPRECATION")
@Singleton
class MemoryMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:AppScope private val appScope: CoroutineScope,
) : ComponentCallbacks2 {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val runtime = Runtime.getRuntime()

    private val _memoryState = MutableStateFlow(MemoryState())
    val memoryState: StateFlow<MemoryState> = _memoryState.asStateFlow()

    private var isMonitoring = false
    private var lastMemoryInfo: ActivityManager.MemoryInfo? = null

    data class MemoryState(
        val isLowMemory: Boolean = false,
        val memoryPressureLevel: Int = ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        val usedHeapMB: Float = 0f,
        val maxHeapMB: Float = 0f,
        val availableHeapMB: Float = 0f,
        val heapUtilization: Float = 0f,
        val systemAvailableMemoryMB: Float = 0f,
        val systemLowMemoryThreshold: Float = 0f,
        val nativeHeapSizeMB: Float = 0f,
        val nativeHeapAllocatedMB: Float = 0f,
        val timestamp: Long = System.currentTimeMillis()
    )

    init {
        if (BuildConfigWrap.DEBUG || Bugs.isDebug) {
            log(TAG, INFO) { "MemoryMonitor initialized" }
            startMemoryMonitoring()
        }
    }

    fun register() {
        try {
            context.registerComponentCallbacks(this)
            log(TAG, DEBUG) { "MemoryMonitor registered for system callbacks" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to register MemoryMonitor: $e" }
        }
    }

    fun unregister() {
        try {
            context.unregisterComponentCallbacks(this)
            log(TAG, DEBUG) { "MemoryMonitor unregistered from system callbacks" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to unregister MemoryMonitor: $e" }
        }
        isMonitoring = false
    }

    private fun startMemoryMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        appScope.launch {
            log(TAG, DEBUG) { "Starting periodic memory monitoring" }
            while (isActive && isMonitoring) {
                try {
                    updateMemoryState()
                    delay(MONITORING_INTERVAL_MS)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Error during memory monitoring: $e" }
                    delay(MONITORING_INTERVAL_MS * 2) // Back off on error
                }
            }
            log(TAG, DEBUG) { "Memory monitoring stopped" }
        }
    }

    private fun updateMemoryState() {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val usedHeap = (runtime.totalMemory() - runtime.freeMemory()) / MB_TO_BYTES
        val maxHeap = runtime.maxMemory() / MB_TO_BYTES
        val availableHeap = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / MB_TO_BYTES
        val heapUtil = (usedHeap / maxHeap) * 100f

        val systemAvailable = memoryInfo.availMem / MB_TO_BYTES
        val lowMemThreshold = memoryInfo.threshold / MB_TO_BYTES

        val nativeHeapSize = Debug.getNativeHeapSize() / MB_TO_BYTES
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / MB_TO_BYTES

        val newState = MemoryState(
            isLowMemory = memoryInfo.lowMemory, // Set by system when available memory < threshold
            memoryPressureLevel = _memoryState.value.memoryPressureLevel,
            usedHeapMB = usedHeap,
            maxHeapMB = maxHeap,
            availableHeapMB = availableHeap,
            heapUtilization = heapUtil,
            systemAvailableMemoryMB = systemAvailable,
            systemLowMemoryThreshold = lowMemThreshold,
            nativeHeapSizeMB = nativeHeapSize,
            nativeHeapAllocatedMB = nativeHeapAllocated,
            timestamp = System.currentTimeMillis()
        )

        _memoryState.value = newState

        // Log memory warnings
        when {
            heapUtil > CRITICAL_HEAP_THRESHOLD -> {
                log(
                    TAG,
                    WARN
                ) { "CRITICAL: Heap utilization ${heapUtil.format()}% (${usedHeap.format()}MB/${maxHeap.format()}MB) - OutOfMemoryError risk!" }
            }

            heapUtil > WARNING_HEAP_THRESHOLD -> {
                log(
                    TAG,
                    WARN
                ) { "WARNING: High heap utilization ${heapUtil.format()}% (${usedHeap.format()}MB/${maxHeap.format()}MB)" }
            }

            memoryInfo.lowMemory -> {
                log(
                    TAG,
                    WARN
                ) { "SYSTEM LOW MEMORY: Available ${systemAvailable.format()}MB, Threshold ${lowMemThreshold.format()}MB" }
            }
        }

        // Detailed debug logging
        if (Bugs.isTrace || shouldLogMemoryDetails(newState)) {
            log(TAG, DEBUG) { buildMemoryReport(newState) }
        }

        lastMemoryInfo = memoryInfo
    }

    private fun shouldLogMemoryDetails(state: MemoryState): Boolean {
        val lastLowMemory = lastMemoryInfo?.lowMemory ?: false
        return state.heapUtilization > WARNING_HEAP_THRESHOLD
                || state.isLowMemory
                || (lastLowMemory != state.isLowMemory)
    }

    private fun buildMemoryReport(state: MemoryState): String {
        return buildString {
            appendLine("=== MEMORY REPORT ===")
            appendLine("Heap: ${state.usedHeapMB.format()}MB used / ${state.maxHeapMB.format()}MB max (${state.heapUtilization.format()}%)")
            appendLine("Available Heap: ${state.availableHeapMB.format()}MB")
            appendLine("Native Heap: ${state.nativeHeapAllocatedMB.format()}MB / ${state.nativeHeapSizeMB.format()}MB")
            appendLine("System Memory: ${state.systemAvailableMemoryMB.format()}MB available")
            appendLine("Low Memory Threshold: ${state.systemLowMemoryThreshold.format()}MB")
            appendLine("System Low Memory: ${state.isLowMemory}")
            appendLine("Last Pressure Level: ${getTrimLevelName(state.memoryPressureLevel)}")
            appendLine("====================")
        }
    }

    override fun onTrimMemory(level: Int) {
        val levelName = getTrimLevelName(level)
        log(TAG, WARN) { "Memory pressure detected: $levelName (level $level)" }

        _memoryState.value = _memoryState.value.copy(
            memoryPressureLevel = level,
            timestamp = System.currentTimeMillis()
        )

        // Force immediate memory state update on trim events
        updateMemoryState()

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                log(TAG, INFO) { "App UI hidden - releasing UI resources recommended" }
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                log(TAG, WARN) { "MODERATE memory pressure - app is running but system is low on memory" }
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                log(TAG, WARN) { "LOW memory pressure - app is running but system is quite low on memory" }
            }

            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                log(TAG, ERROR) { "CRITICAL memory pressure - app is running but system is extremely low on memory!" }
            }

            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                log(TAG, INFO) { "App moved to background - release non-critical resources" }
            }

            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                log(TAG, WARN) { "MODERATE background trim - app is in middle of background LRU list" }
            }

            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                log(TAG, ERROR) { "COMPLETE trim - app will be killed next if memory not freed!" }
            }
        }

        // Log recommendations based on memory pressure
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            val currentState = _memoryState.value
            log(
                TAG,
                WARN
            ) { "Memory optimization recommended - Current heap: ${currentState.heapUtilization.format()}%" }

            if (currentState.heapUtilization > CRITICAL_HEAP_THRESHOLD) {
                log(
                    TAG,
                    ERROR
                ) { "URGENT: Consider calling System.gc() or reducing memory usage - OutOfMemoryError imminent!" }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        log(TAG, DEBUG) { "Configuration changed: ${newConfig}" }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        log(TAG, ERROR) { "System onLowMemory() callback - CRITICAL memory situation!" }
        _memoryState.value = _memoryState.value.copy(
            isLowMemory = true,
            timestamp = System.currentTimeMillis()
        )
        updateMemoryState()
    }

    private fun getTrimLevelName(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
        else -> "UNKNOWN($level)"
    }

    private fun Float.format(): String = "%.1f".format(this)

    fun logCurrentMemoryState() {
        val state = _memoryState.value
        log(TAG, INFO) { buildMemoryReport(state) }
    }

    fun getMemoryPressureRisk(): String = when {
        _memoryState.value.heapUtilization > CRITICAL_HEAP_THRESHOLD -> "CRITICAL - OutOfMemoryError risk"
        _memoryState.value.heapUtilization > WARNING_HEAP_THRESHOLD -> "HIGH - Consider memory optimization"
        _memoryState.value.isLowMemory -> "MEDIUM - System low memory"
        _memoryState.value.memoryPressureLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "MEDIUM - Memory pressure detected"
        else -> "LOW - Memory usage normal"
    }

    companion object {
        private val TAG = logTag("MemoryMonitor")
        private const val MB_TO_BYTES = 1024f * 1024f
        private const val MONITORING_INTERVAL_MS = 10_000L // 10 seconds
        private const val WARNING_HEAP_THRESHOLD = 75f // 75% heap usage
        private const val CRITICAL_HEAP_THRESHOLD = 90f // 90% heap usage
    }
}