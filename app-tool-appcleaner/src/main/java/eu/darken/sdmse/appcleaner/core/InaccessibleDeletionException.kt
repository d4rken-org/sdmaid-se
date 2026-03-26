package eu.darken.sdmse.appcleaner.core

import androidx.navigation.Navigation
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.common.navigation.safeNavigate
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions

class InaccessibleDeletionException(
    override val cause: Throwable
) : IllegalStateException(), HasLocalizedError {

    // TODO how can we get webpage tool and nav actions executed here?
    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = eu.darken.sdmse.appcleaner.R.string.appcleaner_automation_unavailable_title.toCaString(),
        description = caString {
            val sb = StringBuilder()
            sb.append(it.getString(eu.darken.sdmse.appcleaner.R.string.appcleaner_automation_unavailable_body))
            if (cause is HasLocalizedError) {
                sb.append("\n\n")
                sb.append(cause.getLocalizedError().description.get(it))
            }
            sb.toString()
        },
        fixActionLabel = eu.darken.sdmse.common.R.string.setup_title.toCaString(),
        fixAction = {
            val navController = Navigation.findNavController(it, eu.darken.sdmse.common.R.id.nav_host)
            navController.safeNavigate(
                SetupRoute(
                    options = SetupScreenOptions(
                        isOnboarding = false,
                        showCompleted = true,
                        typeFilter = setOf(SetupModule.Type.AUTOMATION),
                    )
                )
            )
        },
    )

}