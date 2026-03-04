package eu.darken.sdmse.automation.core.errors

import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import eu.darken.sdmse.automation.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log

open class AutomationCompatibilityException(
    override val message: String = "SD Maid couldn’t figure out the screen layout. If this keeps happening, your language or setup might not be fully supported. Check for updates or reach out to me so I can fix it."
) : AutomationException(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.automation_error_compatibility_title.toCaString(),
        description = caString {
            """
                ${getString(R.string.automation_error_compatibility_body)}
                
               
                ${getString(eu.darken.sdmse.common.R.string.general_information_for_the_developer)}:
                v${BuildConfigWrap.VERSION_NAME} (${BuildConfigWrap.VERSION_CODE}) ${BuildConfigWrap.FLAVOR} [${BuildConfigWrap.BUILD_TYPE}]
                ${Build.FINGERPRINT}
            """.trimIndent()
        },
        infoActionLabel = eu.darken.sdmse.common.R.string.general_error_report_bug_action.toCaString(),
        infoAction = {
            try {
                val url = "https://github.com/d4rken-org/sdmaid-se/wiki/Bugs".toUri()
                val intent = Intent(Intent.ACTION_VIEW, url).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                it.startActivity(intent)
            } catch (e: Exception) {
                log(WARN) { "Failed to open bug report URL: ${e.asLog()}" }
            }
        }
    )

}