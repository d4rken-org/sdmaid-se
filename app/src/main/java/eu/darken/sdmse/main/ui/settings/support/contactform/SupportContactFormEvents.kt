package eu.darken.sdmse.main.ui.settings.support.contactform

import android.content.Intent

sealed class SupportContactFormEvents {
    data class OpenEmail(val intent: Intent) : SupportContactFormEvents()
}
