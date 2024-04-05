package eu.darken.sdmse.setup

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ui.enableBigText
import eu.darken.sdmse.main.ui.settings.SettingsFragmentDirections
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

suspend fun SetupModule.isComplete() = state.filterIsInstance<SetupModule.State.Current>().first().isComplete

val SetupModule.State.isComplete: Boolean
    get() = this is SetupModule.State.Current && this.isComplete

fun Set<SetupModule.Type>.showFixSetupHint(fragment: Fragment) {
    // If the user navigates back while the snackbar is still showing
    // then we don't have access to the fragment anymore to get the navcontroller
    val navController = fragment.findNavController()
    Snackbar
        .make(
            fragment.requireView(),
            fragment.getString(
                R.string.setup_feature_requires_additional_setup_x,
                this.joinToString(", ") { "\"${fragment.getString(it.labelRes)}\"" }
            ),
            Snackbar.LENGTH_LONG
        )
        .enableBigText()
        .apply { duration = 5000 }
        .setAction(eu.darken.sdmse.common.R.string.general_set_up_action) {
            val direction = SettingsFragmentDirections.goToSetup(
                options = SetupScreenOptions(
                    showCompleted = true,
                    typeFilter = this
                )
            )
            navController.navigate(direction)
        }
        .show()
}