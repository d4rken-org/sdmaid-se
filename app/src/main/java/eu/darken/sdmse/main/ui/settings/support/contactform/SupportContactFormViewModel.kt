package eu.darken.sdmse.main.ui.settings.support.contactform

import android.os.Build
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.EmailTool
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.recorder.core.DebugLogZipper
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportContactFormViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val emailTool: EmailTool,
    private val upgradeRepo: UpgradeRepo,
    private val recorderModule: RecorderModule,
    private val debugLogZipper: DebugLogZipper,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<SupportContactFormEvents>()

    private val currentState = DynamicStateFlow(TAG, vmScope) {
        handle.get<State>(KEY_STATE) ?: State()
    }

    val state = currentState.asLiveData2()

    private val logPickerStater = DynamicStateFlow(TAG, vmScope) {
        LogPickerState(sessions = scanLogSessions())
    }
    val logPickerState = logPickerStater.asLiveData2()

    data class LogPickerState(
        val isRecording: Boolean = false,
        val sessions: List<LogSessionItem> = emptyList(),
        val selectedZip: File? = null,
    )

    data class LogSessionItem(
        val zipFile: File,
        val size: Long,
        val lastModified: Long,
    )

    init {
        recorderModule.state
            .onEach { recState ->
                val sessions = scanLogSessions()
                logPickerStater.updateBlocking {
                    val selected = selectedZip?.takeIf { sel -> sessions.any { it.zipFile == sel } }
                    copy(isRecording = recState.isRecording, sessions = sessions, selectedZip = selected)
                }
            }
            .launchIn(vmScope)
    }

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
            get() = !isSending && descriptionWords >= DESCRIPTION_MIN_WORDS && (!isBug || expectedWords >= EXPECTED_MIN_WORDS)
    }

    enum class Category { BUG, FEATURE, QUESTION }

    enum class Tool { APP_CLEANER, CORPSE_FINDER, SYSTEM_CLEANER, DEDUPLICATOR, ANALYZER, APP_CONTROL, SCHEDULER, GENERAL }

    private suspend fun updateState(block: State.() -> State) {
        currentState.updateBlocking(block)
        handle[KEY_STATE] = currentState.value()
    }

    fun updateCategory(category: Category) = launch {
        updateState { copy(category = category) }
    }

    fun updateTool(tool: Tool) = launch {
        updateState { copy(tool = tool) }
    }

    fun updateDescription(text: String) = launch {
        updateState { copy(description = text) }
    }

    fun updateExpectedBehavior(text: String) = launch {
        updateState { copy(expectedBehavior = text) }
    }

    private fun scanLogSessions(): List<LogSessionItem> {
        return recorderModule.getLogDirectories()
            .flatMap { parent -> parent.listFiles()?.filter { it.extension == "zip" } ?: emptyList() }
            .map { zip ->
                LogSessionItem(
                    zipFile = zip,
                    size = zip.length(),
                    lastModified = zip.lastModified(),
                )
            }
            .sortedByDescending { it.lastModified }
    }

    fun refreshLogSessions() = launch {
        val sessions = scanLogSessions()
        logPickerStater.updateBlocking {
            val selected = selectedZip?.takeIf { sel -> sessions.any { it.zipFile == sel } }
            copy(sessions = sessions, selectedZip = selected)
        }
    }

    fun selectLogSession(zipFile: File) = launch {
        logPickerStater.updateBlocking {
            copy(selectedZip = if (selectedZip == zipFile) null else zipFile)
        }
    }

    fun deleteLogSession(zipFile: File) = launch {
        zipFile.delete()
        File(zipFile.parentFile, zipFile.nameWithoutExtension).deleteRecursively()
        refreshLogSessions()
    }

    fun startRecording() = launch {
        recorderModule.startRecorder()
    }

    fun stopRecording() = launch {
        recorderModule.stopRecorder()
    }

    fun send() = launch {
        val pickerState = logPickerStater.value()
        if (pickerState.isRecording) return@launch

        updateState { copy(isSending = true) }

        try {
            val state = currentState.value()

            val logUri = pickerState.selectedZip?.let {
                try {
                    debugLogZipper.getUriForZip(it)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to zip logs: ${e.asLog()}" }
                    events.postValue(SupportContactFormEvents.ShowError(R.string.support_contact_debuglog_zip_error))
                    null
                }
            }

            val isPro = try {
                upgradeRepo.upgradeInfo.first().isPro
            } catch (e: Exception) {
                null
            }

            val subjectPreview = state.description.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .take(8)
                .joinToString(" ")
            val subject = "[SDMSE][${state.category.name}][${state.tool.name}] $subjectPreview"

            val body = buildString {
                appendLine(state.description)

                if (state.isBug) {
                    appendLine()
                    appendLine("--- Expected Behavior ---")
                    appendLine(state.expectedBehavior)
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
            events.postValue(SupportContactFormEvents.OpenEmail(intent))
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
