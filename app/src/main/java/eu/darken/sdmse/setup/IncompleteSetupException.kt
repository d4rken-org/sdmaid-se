package eu.darken.sdmse.setup

import androidx.navigation.Navigation
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class IncompleteSetupException(private val setupTypes: Set<SetupModule.Type>) : Exception(), HasLocalizedError {

    constructor(setupType: SetupModule.Type) : this(setOf(setupType))

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.general_error_setup_require_label.toCaString(),
        description = caString { ctx ->
            """
                ${ctx.getString(R.string.general_error_setup_require_msg)}
                
                ${setupTypes.joinToString(",") { "'${ctx.getString(it.labelRes)}'" }}
            """.trimIndent()
        },
        fixActionLabel = R.string.setup_title.toCaString(),
        fixAction = {
            val navController = Navigation.findNavController(it, R.id.nav_host)
            val options = SetupScreenOptions(isOnboarding = true, typeFilter = setupTypes)
            navController.navigate(MainDirections.goToSetup(options = options))
        }
    )
}