package eu.darken.sdmse.systemcleaner.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SystemCleanerSettingsFragment : PreferenceFragment2() {

    private val vm: SystemCleanerSettingsFragmentVM by viewModels()

    @Inject lateinit var _settings: SystemCleanerSettings

    override val settings: SystemCleanerSettings by lazy { _settings }
    override val preferenceFile: Int = R.xml.preferences_systemcleaner

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.state.observe2(this) { state ->
            findPreference<Preference>("filter.custom")?.apply {
                setSummary(R.string.systemcleaner_filter_custom_manage_summary)
                if (!state.isPro) appendSummary("\n${getString(R.string.upgrade_feature_requires_pro)}")
            }
        }
    }

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()
        findPreference<Preference>("filter.custom")!!.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setMessage(eu.darken.sdmse.common.R.string.general_todo_msg)
            }.show()
            true
        }
    }

}