package eu.darken.sdmse.automation.core.errors

import android.os.Build
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

open class AutomationCompatibilityException(
    override val message: String = "SD Maid couldn’t figure out the screen layout. If this keeps happening, your language or setup might not be fully supported. Check for updates or reach out to me so I can fix it."
) : AutomationException(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.automation_error_no_consent_title.toCaString(),
        description = caString {
            """
                ${getString(R.string.automation_error_compatibility_body)}
                
               
                ${getString(eu.darken.sdmse.common.R.string.general_information_for_the_developer)}:
                v${BuildConfigWrap.VERSION_NAME} (${BuildConfigWrap.VERSION_CODE}) ${BuildConfigWrap.FLAVOR} [${BuildConfigWrap.BUILD_TYPE}]
                ${Build.FINGERPRINT}
            """.trimIndent()
        },
    )

}