package eu.darken.sdmse.automation.core.errors

import android.view.WindowManager
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

open class AutomationOverlayException(
    cause: WindowManager.BadTokenException,
) : AutomationException(
    "Couldn't show overlay. This sometimes happens on certain devices — a reboot usually helps.",
    cause,
), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.automation_error_overlay_title.toCaString(),
        description = R.string.automation_error_overlay_body.toCaString(),
    )

}