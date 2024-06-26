package eu.darken.sdmse.stats.ui.settings

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.databinding.ViewPreferenceSeekbarBinding
import eu.darken.sdmse.stats.core.StatisticsSettings
import java.time.Duration
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class StatisticsSettingsFragment : PreferenceFragment2() {

    private val vm: StatisticsSettingsViewModel by viewModels()

    @Inject lateinit var _settings: StatisticsSettings

    override val settings: StatisticsSettings by lazy { _settings }
    override val preferenceFile: Int = R.xml.preferences_statistics

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        findPreference<Preference>(settings.reportRetention.keyName)?.apply {
            setOnPreferenceClickListener {
                val dialogLayout = ViewPreferenceSeekbarBinding.inflate(layoutInflater, null, false)
                dialogLayout.apply {
                    slider.valueFrom = 0f
                    slider.valueTo = 365f
                    slider.value = settings.reportRetention.valueBlocking.toDays().toFloat()

                    val getSliderText = { value: Float ->
                        getQuantityString2(R.plurals.statistics_settings_retention_x_days, value.toInt())
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
                    setTitle(R.string.statistics_settings_retention_label)
                    setView(dialogLayout.root)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
                        settings.reportRetention.valueBlocking = Duration.ofDays(dialogLayout.slider.value.toLong())
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
                        settings.reportRetention.valueBlocking = StatisticsSettings.DEFAULT_RETENTION
                    }
                }.show()
                true
            }
        }
    }
}