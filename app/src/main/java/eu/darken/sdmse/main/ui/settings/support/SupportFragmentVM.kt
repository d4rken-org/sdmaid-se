package eu.darken.sdmse.main.ui.settings.support

import android.content.Intent
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.EmailTool
import eu.darken.sdmse.common.InstallId
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.recording.core.RecorderModule
import eu.darken.sdmse.common.livedata.SingleLiveEvent
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SupportFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val emailTool: EmailTool,
    private val installId: InstallId,
    private val dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
) : ViewModel3(dispatcherProvider) {

    val emailEvent = SingleLiveEvent<Intent>()
    val clipboardEvent = SingleLiveEvent<String>()

    val isRecording = recorderModule.state.map { it.isRecording }.asLiveData2()

    fun sendSupportMail() = launch {

        val bodyInfo = StringBuilder("\n\n\n")

        bodyInfo.append("--- Infos for the developer ---\n")

        bodyInfo.append("App version: ").append(BuildConfigWrap.VERSION_DESCRIPTION).append("\n")

        bodyInfo.append("Device: ").append(Build.FINGERPRINT).append("\n")
        bodyInfo.append("Install ID: ").append(installId.id).append("\n")

        val email = EmailTool.Email(
            receipients = listOf("support@darken.eu"),
            subject = "[TODO] Question/Suggestion/Request\n",
            body = bodyInfo.toString()
        )

        emailEvent.postValue(emailTool.build(email))
    }

    fun copyInstallID() = launch {
        clipboardEvent.postValue(installId.id)
    }

    fun startDebugLog() = launch {
        log { "startDebugLog()" }
        recorderModule.startRecorder()
    }
}