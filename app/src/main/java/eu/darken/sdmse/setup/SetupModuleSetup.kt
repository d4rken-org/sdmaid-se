package eu.darken.sdmse.setup

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import eu.darken.sdmse.R
import eu.darken.sdmse.common.navigation.safeNavigate
import eu.darken.sdmse.common.ui.enableBigText

val SetupModule.Type.labelRes: Int
    @StringRes get() = when (this) {
        SetupModule.Type.USAGE_STATS -> R.string.setup_usagestats_title
        SetupModule.Type.AUTOMATION -> R.string.setup_acs_card_title
        SetupModule.Type.SHIZUKU -> R.string.setup_shizuku_card_title
        SetupModule.Type.ROOT -> R.string.setup_root_card_title
        SetupModule.Type.NOTIFICATION -> R.string.setup_notification_title
        SetupModule.Type.SAF -> R.string.setup_saf_card_title
        SetupModule.Type.STORAGE -> R.string.setup_manage_storage_card_title
        SetupModule.Type.INVENTORY -> R.string.setup_inventory_card_title
    }

fun installShowSetupHint() {
    showSetupHint = { fragment, types -> types.showFixSetupHint(fragment) }
}

fun Set<SetupModule.Type>.showFixSetupHint(fragment: Fragment) {
    // If the user navigates back while the snackbar is still showing
    // then we don't have access to the fragment anymore to get the navcontroller
    val navController = fragment.findNavController()
    Snackbar
        .make(
            fragment.requireView(),
            fragment.getString(
                eu.darken.sdmse.common.R.string.setup_feature_requires_additional_setup_x,
                this.joinToString(", ") { "\"${fragment.getString(it.labelRes)}\"" }
            ),
            Snackbar.LENGTH_LONG
        )
        .enableBigText()
        .apply { duration = 5000 }
        .setAction(eu.darken.sdmse.common.R.string.general_set_up_action) {
            navController.safeNavigate(
                SetupRoute(
                    options = SetupScreenOptions(
                        showCompleted = true,
                        typeFilter = this
                    )
                )
            )
        }
        .show()
}
