package eu.darken.sdmse.setup

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ui.enableBigText
import eu.darken.sdmse.main.ui.settings.SettingsFragmentDirections
import kotlinx.coroutines.flow.first

suspend fun SetupModule.isComplete() = state.first()?.isComplete ?: false

fun Collection<SetupModule.Type>.showFixSetupHint(fragment: Fragment) = Snackbar
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
                typeFilter = this.toList()
            )
        )
        fragment.findNavController().navigate(direction)
    }
    .show()