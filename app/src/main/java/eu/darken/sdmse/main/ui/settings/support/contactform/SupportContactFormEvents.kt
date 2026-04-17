package eu.darken.sdmse.main.ui.settings.support.contactform

import android.content.Intent
import androidx.annotation.StringRes

sealed interface SupportContactFormEvents {
    data class OpenEmail(val intent: Intent) : SupportContactFormEvents
    data class OpenUrl(val url: String) : SupportContactFormEvents
    data class ShowError(@StringRes val messageRes: Int) : SupportContactFormEvents
    data object ShowShortRecordingWarning : SupportContactFormEvents
    data object ShowPostSendPrompt : SupportContactFormEvents
}
