package eu.darken.sdmse.appcleaner.ui.settings

import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.preferences.CaveatPreferenceGroup
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.databinding.AppcontrolSettingsAgeSettingDialogBinding
import eu.darken.sdmse.databinding.ViewPreferenceSeekbarBinding
import eu.darken.sdmse.main.ui.settings.SettingsFragmentDirections
import java.time.Duration
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AppCleanerSettingsFragment : PreferenceFragment2() {

    private val vm: AppCleanerSettingsViewModel by viewModels()

    @Inject override lateinit var settings: AppCleanerSettings
    override val preferenceFile: Int = R.xml.preferences_appcleaner

    private val includeRunningCaveat: CaveatPreferenceGroup
        get() = findPreference("include.runningapps.enabled.caveat")!!
    private val includeInaccessibleCaveat: CaveatPreferenceGroup
        get() = findPreference("include.inaccessible.enabled.caveat")!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        findPreference<Preference>(settings.minCacheSizeBytes.keyName)?.apply {
            setOnPreferenceClickListener {
                val dialogLayout = ViewPreferenceSeekbarBinding.inflate(layoutInflater, null, false)
                dialogLayout.apply {
                    slider.valueFrom = 0f
                    slider.valueTo = 100 * 1024f
                    slider.value = (settings.minCacheSizeBytes.valueBlocking / 1024f).coerceAtMost(slider.valueTo)

                    val updateSliderText = {
                        val size = slider.value.toLong() * 1024L
                        sliderValue.text = Formatter.formatShortFileSize(requireContext(), size)
                    }
                    updateSliderText()

                    slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                        override fun onStartTrackingTouch(slider: Slider) {
                            updateSliderText()
                        }

                        override fun onStopTrackingTouch(slider: Slider) {
                            updateSliderText()
                        }
                    })
                }
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.appcleaner_include_minimumsize_label)
                    setView(dialogLayout.root)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
                        settings.minCacheSizeBytes.valueBlocking = dialogLayout.slider.value.toLong() * 1024L
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
                        settings.minCacheSizeBytes.valueBlocking = AppCleanerSettings.MIN_CACHE_SIZE_DEFAULT
                    }
                }.show()
                true
            }
        }

        findPreference<Preference>(settings.minCacheAgeMs.keyName)?.apply {
            setOnPreferenceClickListener {
                val dialogLayout = AppcontrolSettingsAgeSettingDialogBinding.inflate(layoutInflater, null, false)

                var currentValue = settings.minCacheAgeMs.valueBlocking

                var isDays = currentValue > Duration.ofDays(7).toMillis()
                val getBaseUnit = {
                    (if (isDays) Duration.ofDays(1) else Duration.ofHours(1)).toMillis()
                }

                dialogLayout.apply {
                    val updateSlider = {
                        slider.valueFrom = 0f
                        slider.valueTo = if (isDays) 182f else 24 * 6f
                        slider.value = (currentValue / getBaseUnit()).toFloat().coerceAtMost(slider.valueTo)
                    }
                    updateSlider()

                    val updateSliderText = {
                        currentValue = slider.value.toLong() * getBaseUnit()
                        sliderValue.text = DateUtils.getRelativeTimeSpanString(
                            System.currentTimeMillis() - currentValue,
                            System.currentTimeMillis(),
                            if (isDays) DateUtils.DAY_IN_MILLIS else DateUtils.HOUR_IN_MILLIS
                        )
                    }
                    updateSliderText()

                    timeScaleDays.isChecked = isDays
                    timeScaleHours.isChecked = !isDays
                    timeScaleGroup.setOnCheckedChangeListener { _, checkedId ->
                        when (checkedId) {
                            R.id.time_scale_days -> {
                                isDays = true
                            }

                            R.id.time_scale_hours -> {
                                isDays = false
                            }
                        }
                        updateSlider()
                        updateSliderText()
                    }

                    slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                        override fun onStartTrackingTouch(slider: Slider) {
                            updateSliderText()
                        }

                        override fun onStopTrackingTouch(slider: Slider) {
                            updateSliderText()
                        }
                    })
                }
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.appcleaner_include_minimumage_label)
                    setView(dialogLayout.root)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
                        settings.minCacheAgeMs.valueBlocking = currentValue
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
                        settings.minCacheAgeMs.valueBlocking = AppCleanerSettings.MIN_CACHE_AGE_DEFAULT
                    }
                }.show()
                true
            }
        }

        findPreference<Preference>(settings.includeOtherUsersEnabled.keyName)?.apply {
            summary =
                summary.toString() + "\n" + getString(eu.darken.sdmse.common.R.string.general_root_required_message)
        }

        includeRunningCaveat.apply {
            caveatMessage = getString(
                R.string.setup_feature_requires_x_setup,
                getString(R.string.setup_usagestats_title)
            )
            caveatAction = getString(eu.darken.sdmse.common.R.string.general_fix_action)
            caveatClickListener = {
                SettingsFragmentDirections.goToSetup(showCompleted = true).navigate()
                true
            }
        }

        includeInaccessibleCaveat.apply {
            caveatMessage = getString(
                R.string.setup_feature_requires_x_setup,
                getString(R.string.setup_usagestats_title)
            )
            caveatAction = getString(eu.darken.sdmse.common.R.string.general_fix_action)
            caveatClickListener = {
                SettingsFragmentDirections.goToSetup(showCompleted = true).navigate()
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.state.observe2(this) { state ->
            includeRunningCaveat.showCaveat = !state.hasUsageStats
            includeInaccessibleCaveat.showCaveat = !state.hasUsageStats
        }
    }
}