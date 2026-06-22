package eu.darken.sdmse.main.ui.settings.support.contactform

import android.os.Build
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.EmailTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.parcelize.Parcelize
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportContactFormViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val emailTool: EmailTool,
    private val upgradeRepo: UpgradeRepo,
    private val sessionManager: DebugLogSessionManager,
) : ViewModel4(dispatcherProvider, TAG) {

    val events = SingleEventFlow<SupportContactFormEvents>()

    @Volatile
    private var pendingSendCheck = false

    private val formState = MutableStateFlow(handle.get<State>(KEY_STATE) ?: State())

    val state: StateFlow<State> = formState

    private val selectedSessionId = MutableStateFlow<SessionId?>(null)
    private val pendingSessionId = MutableStateFlow<SessionId?>(null)

    val logPickerState: StateFlow<LogPickerState> = combine(
        sessionManager.sessions,
        selectedSessionId,
    ) { sessions, selectedId ->
        val isRecording = sessions.any { it is DebugLogSession.Recording }
        val isZipping = sessions.any { it is DebugLogSession.Zipping }
        val finished = sessions.filterIsInstance<DebugLogSession.Finished>()
        val validatedSelection = selectedId?.takeIf { id -> finished.any { it.id == id } }
        LogPickerState(
            isRecording = isRecording,
            isZipping = isZipping,
            sessions = finished.map {
                LogSessionItem(
                    sessionId = it.id,
                    zipFile = it.zipFile,
                    size = it.compressedSize,
                    lastModified = it.createdAt.toEpochMilli(),
                )
            },
            selectedSessionId = validatedSelection,
        )
    }.safeStateIn(LogPickerState()) { LogPickerState() }

    init {
        // Auto-select a session as soon as its pending zip completes. Clear selection on failure.
        launch {
            sessionManager.sessions.collect { sessions ->
                val pending = pendingSessionId.value ?: return@collect
                val finished = sessions.filterIsInstance<DebugLogSession.Finished>().find { it.id == pending }
                if (finished != null) {
                    pendingSessionId.value = null
                    selectedSessionId.value = finished.id
                    return@collect
                }
                val failed = sessions.filterIsInstance<DebugLogSession.Failed>().find { it.id == pending }
                if (failed != null) pendingSessionId.value = null
            }
        }
    }

    data class LogPickerState(
        val isRecording: Boolean = false,
        val isZipping: Boolean = false,
        val sessions: List<LogSessionItem> = emptyList(),
        val selectedSessionId: SessionId? = null,
    )

    data class LogSessionItem(
        val sessionId: SessionId,
        val zipFile: File,
        val size: Long,
        val lastModified: Long,
    )

    @Parcelize
    data class State(
        val category: Category = Category.QUESTION,
        val tool: Tool = Tool.GENERAL,
        val description: String = "",
        val expectedBehavior: String = "",
        val isSending: Boolean = false,
    ) : Parcelable {
        val isBug: Boolean get() = category == Category.BUG
        val descriptionWords: Int
            get() = description.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        val expectedWords: Int
            get() = expectedBehavior.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        val canSend: Boolean
            get() = !isSending && descriptionWords >= DESCRIPTION_MIN_WORDS &&
                (!isBug || expectedWords >= EXPECTED_MIN_WORDS)
    }

    enum class Category { BUG, FEATURE, QUESTION }

    enum class Tool { APP_CLEANER, CORPSE_FINDER, SYSTEM_CLEANER, DEDUPLICATOR, ANALYZER, APP_CONTROL, SCHEDULER, GENERAL }

    private fun updateState(block: State.() -> State) {
        formState.update { it.block() }
        handle[KEY_STATE] = formState.value
    }

    fun updateCategory(category: Category) = updateState { copy(category = category) }
    fun updateTool(tool: Tool) = updateState { copy(tool = tool) }
    fun updateDescription(text: String) = updateState { copy(description = text) }
    fun updateExpectedBehavior(text: String) = updateState { copy(expectedBehavior = text) }

    fun refreshLogSessions() {
        sessionManager.refresh()
    }

    fun selectLogSession(sessionId: SessionId) {
        selectedSessionId.update { current -> if (current == sessionId) null else sessionId }
    }

    fun deleteLogSession(sessionId: SessionId) = launch {
        sessionManager.delete(sessionId)
    }

    fun startRecording() = launch {
        sessionManager.startRecording()
    }

    fun stopRecording() = launch {
        when (val result = sessionManager.requestStopRecording()) {
            is RecorderModule.StopResult.TooShort -> {
                events.emit(SupportContactFormEvents.ShowShortRecordingWarning)
            }
            is RecorderModule.StopResult.Stopped -> pendingSessionId.value = result.sessionId
            is RecorderModule.StopResult.NotRecording -> Unit
        }
    }

    fun confirmStopRecording() = launch {
        val result = sessionManager.forceStopRecording() ?: return@launch
        pendingSessionId.value = result.sessionId
    }

    fun checkPendingSend() {
        if (!pendingSendCheck) return
        pendingSendCheck = false
        events.tryEmit(SupportContactFormEvents.ShowPostSendPrompt)
    }

    /**
     * Called by the host composable ONLY after `startActivity(intent)` succeeds. Arming the
     * post-send prompt before the intent could leak a "message sent?" dialog on devices without
     * an email app (the legacy fragment did this).
     */
    fun markEmailLaunched() {
        pendingSendCheck = true
    }

    fun openUrl(url: String) {
        events.tryEmit(SupportContactFormEvents.OpenUrl(url))
    }

    fun send() = launch {
        val picker = logPickerState.value
        if (picker.isRecording) return@launch

        updateState { copy(isSending = true) }
        try {
            val snapshot = formState.value

            val logUri = picker.selectedSessionId?.let { id ->
                try {
                    sessionManager.getZipUri(id)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to zip logs: ${e.asLog()}" }
                    events.emit(SupportContactFormEvents.ShowError(R.string.support_contact_debuglog_zip_error))
                    return@launch
                }
            }

            val isPro = try {
                upgradeRepo.upgradeInfo.first().isPro
            } catch (_: Exception) {
                null
            }

            val subjectPreview = snapshot.description.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .take(8)
                .joinToString(" ")
            val subject = "[SDMSE][${snapshot.category.name}][${snapshot.tool.name}] $subjectPreview"

            val body = buildString {
                appendLine(snapshot.description)

                if (snapshot.isBug) {
                    appendLine()
                    appendLine("--- Expected Behavior ---")
                    appendLine(snapshot.expectedBehavior)
                }

                val proIndicator = when (isPro) {
                    true -> " \u2605"
                    false -> " \u2606"
                    null -> ""
                }
                appendLine()
                appendLine("--- Device Info ---")
                appendLine("App: ${BuildConfigWrap.VERSION_DESCRIPTION}$proIndicator")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            }

            val email = EmailTool.Email(
                receipients = listOf(SUPPORT_EMAIL),
                subject = subject,
                body = body,
                attachment = logUri,
            )

            val intent = emailTool.build(email, offerChooser = true)
            events.emit(SupportContactFormEvents.OpenEmail(intent))
        } finally {
            updateState { copy(isSending = false) }
        }
    }

    companion object {
        const val DESCRIPTION_MIN_WORDS = 20
        const val EXPECTED_MIN_WORDS = 10
        private const val KEY_STATE = "form_state"
        private const val SUPPORT_EMAIL = "support@darken.eu"
        private val TAG = logTag("Support", "ContactForm", "ViewModel")
    }
}
