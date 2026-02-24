package eu.darken.sdmse.common.error

import androidx.navigation.Navigation
import androidx.navigation.findNavController
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionFragmentArgs
import eu.darken.sdmse.setup.IncompleteSetupException
import eu.darken.sdmse.setup.SetupScreenOptions
import eu.darken.sdmse.setup.labelRes

private fun IncompleteSetupException.toLocalizedError(): LocalizedError = LocalizedError(
    throwable = this,
    label = eu.darken.sdmse.R.string.general_error_setup_require_label.toCaString(),
    description = caString { ctx ->
        """
            ${ctx.getString(eu.darken.sdmse.R.string.general_error_setup_require_msg)}

            ${setupTypes.joinToString(",") { "'${ctx.getString(it.labelRes)}'" }}
        """.trimIndent()
    },
    fixActionLabel = eu.darken.sdmse.common.R.string.setup_title.toCaString(),
    fixAction = {
        val navController = Navigation.findNavController(it, eu.darken.sdmse.R.id.nav_host)
        val options = SetupScreenOptions(isOnboarding = true, typeFilter = setupTypes)
        navController.navigate(eu.darken.sdmse.MainDirections.goToSetup(options = options))
    }
)

fun installErrorDialogCustomizer() {
    errorDialogCustomizer = { error, activity ->
        when {
            error is IncompleteSetupException -> error.toLocalizedError()
            error is WriteException && error.path != null -> {
                error.localized(activity).copy(
                    infoActionLabel = eu.darken.sdmse.R.string.exclusion_create_action.toCaString(),
                    infoAction = { ctx ->
                        ctx.findNavController(eu.darken.sdmse.R.id.nav_host).navigate(
                            resId = eu.darken.sdmse.R.id.goToPathExclusionEditor,
                            args = PathExclusionFragmentArgs(
                                initial = PathExclusionEditorOptions(targetPath = error.path!!)
                            ).toBundle()
                        )
                    }
                )
            }

            else -> null
        }
    }
}
