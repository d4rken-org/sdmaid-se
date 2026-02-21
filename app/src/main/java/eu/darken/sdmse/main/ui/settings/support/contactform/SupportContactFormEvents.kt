package eu.darken.sdmse.main.ui.settings.support.contactform

import android.content.Intent
import androidx.annotation.StringRes

sealed class SupportContactFormEvents {
    data class OpenEmail(val intent: Intent) : SupportContactFormEvents()
    data class ShowError(@StringRes val messageRes: Int) : SupportContactFormEvents()
}
