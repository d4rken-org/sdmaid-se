package eu.darken.sdmse.main.ui.settings.support.contactform

import android.os.Build
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.EmailTool
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.root.RootSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.shizuku.ShizukuSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupModule
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@HiltViewModel
class SupportContactFormViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val emailTool: EmailTool,
    private val setupManager: SetupManager,
) : ViewModel3(dispatcherProvider) {

    val events = SingleLiveEvent<SupportContactFormEvents>()

    private val currentState = DynamicStateFlow(TAG, vmScope) {
        handle.get<State>(KEY_STATE) ?: State()
    }

    val state = currentState.asLiveData2()

    @Parcelize
    data class State(
        val category: Category = Category.BUG,
        val tool: Tool = Tool.GENERAL,
        val description: String = "",
        val expectedBehavior: String = "",
        val triedRestart: Boolean = false,
        val triedClearCache: Boolean = false,
        val triedReboot: Boolean = false,
        val triedPermissions: Boolean = false,
        val triedOther: String = "",
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

    fun toggleTriedRestart(checked: Boolean) = launch {
        updateState { copy(triedRestart = checked) }
    }

    fun toggleTriedClearCache(checked: Boolean) = launch {
        updateState { copy(triedClearCache = checked) }
    }

    fun toggleTriedReboot(checked: Boolean) = launch {
        updateState { copy(triedReboot = checked) }
    }

    fun toggleTriedPermissions(checked: Boolean) = launch {
        updateState { copy(triedPermissions = checked) }
    }

    fun updateTriedOther(text: String) = launch {
        updateState { copy(triedOther = text) }
    }

    fun send() = launch {
        updateState { copy(isSending = true) }

        try {
            val state = currentState.value()

            val categoryLabel = when (state.category) {
                Category.BUG -> "Bug Report"
                Category.FEATURE -> "Feature Request"
                Category.QUESTION -> "Question"
            }

            val toolLabel = when (state.tool) {
                Tool.APP_CLEANER -> "AppCleaner"
                Tool.CORPSE_FINDER -> "CorpseFinder"
                Tool.SYSTEM_CLEANER -> "SystemCleaner"
                Tool.DEDUPLICATOR -> "Deduplicator"
                Tool.ANALYZER -> "Analyzer"
                Tool.APP_CONTROL -> "AppControl"
                Tool.SCHEDULER -> "Scheduler"
                Tool.GENERAL -> "General"
            }

            val setupInfo = getSetupInfo()

            val subjectPreview = state.description.take(60).replace("\n", " ")
            val subject = "[$categoryLabel][$toolLabel] $subjectPreview"

            val body = buildString {
                appendLine("Category: $categoryLabel")
                appendLine("Tool: $toolLabel")
                appendLine()
                appendLine("--- Description ---")
                appendLine(state.description)

                if (state.isBug) {
                    appendLine()
                    appendLine("--- Expected Behavior ---")
                    appendLine(state.expectedBehavior)
                    appendLine()
                    appendLine("--- What I Tried ---")
                    appendLine("[${if (state.triedRestart) "x" else " "}] Restarted the app")
                    appendLine("[${if (state.triedClearCache) "x" else " "}] Cleared SD Maid's cache")
                    appendLine("[${if (state.triedReboot) "x" else " "}] Rebooted the device")
                    appendLine("[${if (state.triedPermissions) "x" else " "}] Checked permissions")
                    if (state.triedOther.isNotBlank()) {
                        appendLine("Additional: ${state.triedOther}")
                    }
                }

                appendLine()
                appendLine("--- Device Info ---")
                appendLine("App: ${BuildConfigWrap.VERSION_DESCRIPTION}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine()
                appendLine("--- Setup ---")
                append(setupInfo)
            }

            val email = EmailTool.Email(
                receipients = listOf(SUPPORT_EMAIL),
                subject = subject,
                body = body,
            )

            val intent = emailTool.build(email, offerChooser = true)
            events.postValue(SupportContactFormEvents.OpenEmail(intent))
        } finally {
            updateState { copy(isSending = false) }
        }
    }

    private suspend fun getSetupInfo(): String {
        val moduleStates = withTimeoutOrNull(5_000L) {
            setupManager.state
                .filterNot { it.isWorking }
                .map { it.moduleStates.filterIsInstance<SetupModule.State.Current>() }
                .first()
        } ?: return "Setup info unavailable (timed out)\n"

        return buildString {
            for (module in moduleStates.sortedBy { it.type.ordinal }) {
                val status = when (module) {
                    is RootSetupModule.Result -> when {
                        module.ourService -> "granted"
                        module.useRoot == false -> "disabled"
                        !module.isInstalled -> "not available"
                        else -> "not granted"
                    }

                    is ShizukuSetupModule.Result -> when {
                        module.ourService -> "granted"
                        !module.isCompatible -> "not available"
                        module.useShizuku == false -> "disabled"
                        !module.isInstalled -> "not installed"
                        else -> "not granted"
                    }

                    is SAFSetupModule.Result -> {
                        val granted = module.paths.count { it.hasAccess }
                        "${granted}/${module.paths.size} areas"
                    }

                    is StorageSetupModule.Result -> {
                        val granted = module.paths.count { it.hasAccess }
                        val perms = if (module.missingPermission.isEmpty()) "ok" else "missing"
                        "permission $perms, ${granted}/${module.paths.size} paths"
                    }

                    is AutomationSetupModule.Result -> when {
                        module.isNotRequired -> "not required"
                        module.isServiceRunning -> "running"
                        module.isServiceEnabled -> "enabled, not running"
                        module.hasConsent == true -> "consent given, not enabled"
                        module.hasConsent == false -> "declined"
                        else -> "not set up"
                    }

                    else -> if (module.isComplete) "ok" else "missing"
                }
                appendLine("${module.type.name}: $status")
            }
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
