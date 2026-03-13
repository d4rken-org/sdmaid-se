package eu.darken.sdmse.squeezer.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SqueezerSettingsFragment : PreferenceFragment2() {

    private val vm: SqueezerSettingsViewModel by viewModels()

    @Inject lateinit var squeezerSettings: SqueezerSettings

    override val settings: SqueezerSettings by lazy { squeezerSettings }
    override val preferenceFile: Int = R.xml.preferences_squeezer

    private val historyPref: Preference
        get() = findPreference("compression.history.info")!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(this) { state ->
            log(TAG) { "Updating state: $state" }

            historyPref.summary = if (state.historyCount > 0) {
                val (formatted, _) = ByteFormatter.formatSize(requireContext(), state.historyDatabaseSize)
                resources.getQuantityString(R.plurals.squeezer_history_summary, state.historyCount, state.historyCount, formatted)
            } else {
                getString(R.string.squeezer_history_empty)
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        historyPref.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(R.string.squeezer_history_clear_title)
                setMessage(R.string.squeezer_history_clear_message)
                setPositiveButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
                    vm.clearHistory()
                }
                setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
            }.show()
            true
        }
    }

    companion object {
        private val TAG = logTag("Settings", "Squeezer", "Fragment")
    }
}
