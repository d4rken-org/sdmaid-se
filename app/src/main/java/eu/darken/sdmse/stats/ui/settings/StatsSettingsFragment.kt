package eu.darken.sdmse.stats.ui.settings

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.uix.PreferenceFragment3
import eu.darken.sdmse.databinding.ViewPreferenceSeekbarBinding
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

        settings.retentionReports.setupDaySetter(
            R.string.stats_settings_retention_reports_label,
            StatsSettings.DEFAULT_RETENTION_REPORTS
        )
        settings.retentionPaths.setupDaySetter(
            R.string.stats_settings_retention_paths_label,
            StatsSettings.DEFAULT_RETENTION_PATHS
        )

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
            preferenceSize.summary = Formatter.formatShortFileSize(requireContext(),
                state.databaseSize.takeIf { it > 32 * 1024 } ?: 0L
            )
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun DataStoreValue<Duration>.setupDaySetter(
        @StringRes titleRes: Int,
        default: Duration,
    ) = findPreference<Preference>(keyName)?.setOnPreferenceClickListener {
        val dialogLayout = ViewPreferenceSeekbarBinding.inflate(layoutInflater, null, false)
        dialogLayout.apply {
            slider.valueFrom = 0f
            slider.valueTo = 365f
            slider.value = valueBlocking.toDays().toFloat()

            val getSliderText = { value: Float ->
                getQuantityString2(R.plurals.stats_settings_retention_x_days, value.toInt())
            }
            slider.setLabelFormatter { getSliderText(it) }
            sliderValue.text = getSliderText(slider.value)

            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    sliderValue.text = getSliderText(slider.value)
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    sliderValue.text = getSliderText(slider.value)
                }
            })
        }
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(titleRes)
            setView(dialogLayout.root)
            setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
                valueBlocking = Duration.ofDays(dialogLayout.slider.value.toLong())
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
            setNeutralButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
                valueBlocking = default
            }
        }.show()
        true
    }
}