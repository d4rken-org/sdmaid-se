package eu.darken.sdmse.automation.core.errors

import androidx.navigation.Navigation
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.SetupScreenOptions

open class AutomationNoConsentException(
    override val message: String = "User has not consented to accessibility service being used"
) : AutomationUnavailableException(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.automation_error_no_consent_title.toCaString(),
        description = R.string.automation_error_no_consent_body.toCaString(),
        fixActionLabel = R.string.setup_title.toCaString(),
        fixAction = {
            val navController = Navigation.findNavController(it, R.id.nav_host)
            val options = SetupScreenOptions(showCompleted = true, typeFilter = setOf(SetupModule.Type.AUTOMATION))
            navController.navigate(MainDirections.goToSetup(options = options))
        }
    )

}