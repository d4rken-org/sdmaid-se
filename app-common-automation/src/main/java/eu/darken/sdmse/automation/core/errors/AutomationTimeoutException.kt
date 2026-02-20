package eu.darken.sdmse.automation.core.errors

import android.content.Intent
import androidx.core.net.toUri
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import kotlinx.coroutines.TimeoutCancellationException

open class AutomationTimeoutException(
    cause: TimeoutCancellationException,
) : AutomationException(
    "SD Maid couldn't complete the necessary steps within the timelimit. This could mean that I need to adjust the app for your device. Consider reaching out to me so I can fix it.",
    cause,
), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.automation_error_timeout_title.toCaString(),
        description = R.string.automation_error_timeout_body.toCaString(),
        infoActionLabel = eu.darken.sdmse.common.R.string.general_error_report_bug_action.toCaString(),
        infoAction = {
            try {
                val url = "https://github.com/d4rken-org/sdmaid-se/wiki/Bugs".toUri()
                val intent = Intent(Intent.ACTION_VIEW, url).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                it.startActivity(intent)
            } catch (e: Exception) {
                e.asErrorDialogBuilder(it).show()
            }
        }
    )

}