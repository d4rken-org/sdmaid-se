package testhelpers.automation

import eu.darken.sdmse.automation.core.AutomationEvent
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.AutomationService
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.Progress
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import testhelpers.TestACSNodeInfo

/**
 * Test implementation of [AutomationHost] for testing automation specs.
 *
 * Provides:
 * - Dynamic window roots via [setWindowRoot]
 * - Event emission via [emitEvent] and [transitionTo]
 * - Progress tracking for assertions
 *
 * Usage:
 * ```kotlin
 * val host = TestAutomationHost(testScope)
 *
 * // Set static window root
 * host.setWindowRoot(buildTestTree("..."))
 *
 * // Or emit events for window detection
 * host.transitionTo(tree, "com.android.settings")
 * ```
 */
class TestAutomationHost(
    private val testScope: CoroutineScope,
) : AutomationHost {

    // Dynamic window root
    private val _windowRoot = MutableStateFlow<TestACSNodeInfo?>(null)

    override suspend fun windowRoot(): ACSNodeInfo? = _windowRoot.value

    // Event stream for specs that use host.events
    private val _events = MutableSharedFlow<AutomationEvent>(replay = 0, extraBufferCapacity = 10)
    override val events: Flow<AutomationEvent> = _events

    // State (always available in tests by default)
    private val _state = MutableStateFlow(AutomationHost.State(hasOverlay = false, passthrough = false))
    override val state: Flow<AutomationHost.State> = _state

    // Scope for coroutines
    override val scope: CoroutineScope = testScope

    // Service mock - using AutomationService so casts work
    override val service: AutomationService = mockk(relaxed = true)

    // Progress tracking
    private var _progressData: Progress.Data? = null

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        _progressData = update(_progressData)
    }

    /**
     * Get the current progress data for assertions.
     */
    fun getProgressData(): Progress.Data? = _progressData

    override suspend fun changeOptions(action: (AutomationHost.Options) -> AutomationHost.Options) {
        // No-op in tests
    }

    // ========== Test Helpers ==========

    /**
     * Set the current window root.
     */
    fun setWindowRoot(root: TestACSNodeInfo?) {
        _windowRoot.value = root
    }

    /**
     * Get the current window root for assertions.
     */
    fun getCurrentRoot(): TestACSNodeInfo? = _windowRoot.value

    /**
     * Emit an automation event. Use for testing event-based window detection.
     */
    suspend fun emitEvent(
        pkgId: String,
        eventType: Int = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    ) {
        _events.emit(TestAutomationEvent(pkgId = pkgId.toPkgId(), eventType = eventType))
    }

    /**
     * Simulate a window transition: set new root and emit event.
     * Use for testing specs that wait for specific window events.
     */
    suspend fun transitionTo(root: TestACSNodeInfo, pkgId: String) {
        setWindowRoot(root)
        emitEvent(pkgId)
    }

    /**
     * Set the host state (overlay visibility, passthrough mode).
     */
    fun setHostState(hasOverlay: Boolean = false, passthrough: Boolean = false) {
        _state.value = AutomationHost.State(hasOverlay = hasOverlay, passthrough = passthrough)
    }
}
