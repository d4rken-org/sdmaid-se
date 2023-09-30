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
import eu.darken.sdmse.common.preferences.BadgedCheckboxPreference
import eu.darken.sdmse.common.preferences.ListPreference2
import eu.darken.sdmse.common.preferences.setupWithEnum
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.databinding.AppcontrolSettingsAgeSettingDialogBinding
import eu.darken.sdmse.databinding.ViewPreferenceSeekbarBinding
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.showFixSetupHint
import java.time.Duration
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AppCleanerSettingsFragment : PreferenceFragment2() {

    private val vm: AppCleanerSettingsViewModel by viewModels()

    @Inject override lateinit var settings: AppCleanerSettings
    override val preferenceFile: Int = R.xml.preferences_appcleaner

    private val includeOtherUsers: BadgedCheckboxPreference
        get() = findPreference(settings.includeOtherUsersEnabled.keyName)!!

    private val includeRunningApps: BadgedCheckboxPreference
        get() = findPreference(settings.includeRunningAppsEnabled.keyName)!!

    private val includeInaccessibleCaches: BadgedCheckboxPreference
        get() = findPreference(settings.includeInaccessibleEnabled.keyName)!!

    private val romTypeOverride: ListPreference2
        get() = findPreference(settings.romTypeDetection.keyName)!!

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()
        romTypeOverride.setupWithEnum(settings.romTypeDetection)

        findPreference<Preference>(settings.minCacheSizeBytes.keyName)?.apply {
            setOnPreferenceClickListener {
                val dialogLayout = ViewPreferenceSeekbarBinding.inflate(layoutInflater, null, false)
                dialogLayout.apply {
                    slider.valueFrom = 0f
                    slider.valueTo = 100 * 1024f
                    slider.value = (settings.minCacheSizeBytes.valueBlocking / 1024f).coerceAtMost(slider.valueTo)

                    val getSliderText = { value: Float ->
                        val size = value.toLong() * 1024L
                        Formatter.formatShortFileSize(requireContext(), size)
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

                    val formatSliderText = { value: Float ->
                        val timeNow = System.currentTimeMillis()
                        val timeSpan = timeNow - value.toLong() * getBaseUnit()
                        val flags = when {
                            isDays -> when {
                                Duration.ofMillis(timeNow - timeSpan).toDays() < 7 -> DateUtils.DAY_IN_MILLIS
                                else -> DateUtils.WEEK_IN_MILLIS
                            }

                            else -> DateUtils.HOUR_IN_MILLIS
                        }
                        DateUtils.getRelativeTimeSpanString(timeSpan, timeNow, flags).toString()
                    }

                    val updateValue = {
                        currentValue = slider.value.toLong() * getBaseUnit()
                        sliderValue.text = formatSliderText(slider.value)
                    }
                    updateValue()

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
                        updateValue()
                    }

                    slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                        override fun onStartTrackingTouch(slider: Slider) {
                            updateValue()
                        }

                        override fun onStopTrackingTouch(slider: Slider) {
                            updateValue()
                        }
                    })
                    slider.setLabelFormatter { formatSliderText(it) }
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

        includeOtherUsers.badgedAction = {
            listOf(SetupModule.Type.ROOT).showFixSetupHint(this)
        }
        includeRunningApps.badgedAction = {
            listOf(SetupModule.Type.USAGE_STATS).showFixSetupHint(this)
        }
        includeInaccessibleCaches.badgedAction = {
            listOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.AUTOMATION).showFixSetupHint(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.state.observe2(this) { state ->
            includeOtherUsers.isRestricted = !state.isOtherUsersAvailable
            includeRunningApps.isRestricted = !state.isRunningAppsDetectionAvailable
            includeInaccessibleCaches.apply {
                isRestricted = !state.isInaccessibleCacheAvailable
                isVisible = state.isAcsRequired
            }
        }
    }
}