package eu.darken.sdmse.common.error

import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.exclusion.ui.PathExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.setup.IncompleteSetupException
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions

@get:StringRes
private val SetupModule.Type.labelRes: Int
    get() = when (this) {
        SetupModule.Type.USAGE_STATS -> R.string.setup_usagestats_title
        SetupModule.Type.AUTOMATION -> R.string.setup_acs_card_title
        SetupModule.Type.SHIZUKU -> R.string.setup_shizuku_card_title
        SetupModule.Type.ROOT -> R.string.setup_root_card_title
        SetupModule.Type.NOTIFICATION -> R.string.setup_notification_title
        SetupModule.Type.SAF -> R.string.setup_saf_card_title
        SetupModule.Type.STORAGE -> R.string.setup_manage_storage_card_title
        SetupModule.Type.INVENTORY -> R.string.setup_inventory_card_title
    }

private fun IncompleteSetupException.toLocalizedError(): LocalizedError = LocalizedError(
    throwable = this,
    label = R.string.general_error_setup_require_label.toCaString(),
    description = caString { ctx ->
        """
            ${ctx.getString(R.string.general_error_setup_require_msg)}

            ${setupTypes.joinToString(",") { "'${ctx.getString(it.labelRes)}'" }}
        """.trimIndent()
    },
    fixActionLabel = eu.darken.sdmse.common.R.string.setup_title.toCaString(),
    fixActionRoute = SetupRoute(
        options = SetupScreenOptions(isOnboarding = true, typeFilter = setupTypes),
    ),
)

fun installErrorDialogCustomizer() {
    errorDialogCustomizer = { error, activity ->
        when {
            error is IncompleteSetupException -> error.toLocalizedError()
            error is WriteException && error.path != null -> {
                error.localized(activity).copy(
                    infoActionLabel = eu.darken.sdmse.common.exclusion.R.string.exclusion_create_action.toCaString(),
                    infoActionRoute = PathExclusionEditorRoute(
                        initial = PathExclusionEditorOptions(targetPath = error.path!!),
                    ),
                )
            }

            else -> null
        }
    }
}
