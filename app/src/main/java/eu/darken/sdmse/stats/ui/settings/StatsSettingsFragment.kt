package eu.darken.sdmse.stats.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.ui.AgeInputDialog
import eu.darken.sdmse.common.uix.PreferenceFragment3
import eu.darken.sdmse.stats.core.StatsSettings
import java.time.Duration
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class StatsSettingsFragment : PreferenceFragment3() {

    override val vm: StatsSettingsViewModel by viewModels()

    @Inject lateinit var _settings: StatsSettings

    override val settings: StatsSettings by lazy { _settings }
    override val preferenceFile: Int = R.xml.preferences_statistics

    private val preferenceSize: Preference
        get() = findPreference("reports.size")!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        settings.retentionReports.let { setting ->
            findPreference<Preference>(setting.keyName)!!.setOnPreferenceClickListener { it ->
                AgeInputDialog(
                    requireActivity(),
                    titleRes = R.string.stats_settings_retention_reports_label,
                    currentAge = setting.valueBlocking,
                    maximumAge = Duration.ofDays(365),
                    onReset = { setting.valueBlocking = StatsSettings.DEFAULT_RETENTION_REPORTS },
                    onSave = { setting.valueBlocking = it }
                ).show()
                true
            }
        }
        settings.retentionPaths.let { setting ->
            findPreference<Preference>(setting.keyName)!!.setOnPreferenceClickListener { it ->
                AgeInputDialog(
                    requireActivity(),
                    titleRes = R.string.stats_settings_retention_paths_label,
                    currentAge = setting.valueBlocking,
                    maximumAge = Duration.ofDays(365),
                    onReset = { setting.valueBlocking = StatsSettings.DEFAULT_RETENTION_PATHS },
                    onSave = { setting.valueBlocking = it }
                ).show()
                true
            }
        }

        preferenceSize.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(R.string.stats_settings_reset_all_label)
                setMessage(R.string.stats_settings_reset_all_desc)
                setPositiveButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ -> vm.resetAll() }
                setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
            }.show()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2 { state ->
            preferenceSize.summary = getQuantityString2(
                eu.darken.sdmse.common.R.plurals.result_x_items,
                state.reportsCount
            )
        }
        super.onViewCreated(view, savedInstanceState)
    }
}