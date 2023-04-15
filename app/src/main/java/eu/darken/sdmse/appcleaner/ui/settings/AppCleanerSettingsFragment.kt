package eu.darken.sdmse.appcleaner.ui.settings

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.databinding.ViewPreferenceSeekbarBinding
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class AppCleanerSettingsFragment : PreferenceFragment2() {

    private val vm: AppCleanerSettingsFragmentVM by viewModels()

    @Inject override lateinit var settings: AppCleanerSettings
    override val preferenceFile: Int = R.xml.preferences_appcleaner

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()
        findPreference<Preference>(settings.includeOtherUsersEnabled.keyName)?.apply {
            summary = summary.toString() + "\n" + getString(R.string.general_root_required_message)
        }
        findPreference<Preference>(settings.minCacheSizeBytes.keyName)?.apply {
            setOnPreferenceClickListener {
                val dialogLayout = ViewPreferenceSeekbarBinding.inflate(layoutInflater, null, false)
                dialogLayout.apply {
                    slider.valueFrom = 0f
                    slider.valueTo = 105f
                    slider.value = (settings.minCacheSizeBytes.valueBlocking / (1024 * 1024L)).toFloat()
                        .coerceAtMost(slider.valueTo)

                    val updateSliderText = {
                        val size = slider.value.toLong() * 1024L * 1024L
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
                    setPositiveButton(R.string.general_save_action) { _, _ ->
                        settings.minCacheSizeBytes.valueBlocking = dialogLayout.slider.value.toLong() * 1024L * 1024L
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                }.show()
                true
            }
        }

        findPreference<Preference>(settings.minCacheAgeMs.keyName)?.apply {
            setOnPreferenceClickListener {
                val dialogLayout = ViewPreferenceSeekbarBinding.inflate(layoutInflater, null, false)
                dialogLayout.apply {
                    slider.valueFrom = 0f
                    slider.valueTo = 60 * 24 * 6f
                    slider.value = (
                            settings.minCacheAgeMs.valueBlocking / (60 * 1000L)
                            ).toFloat().coerceAtMost(slider.valueTo)

                    val updateSliderText = {
                        val millis = slider.value.toLong() * 60 * 1000L
                        sliderValue.text = DateUtils.getRelativeTimeSpanString(
                            System.currentTimeMillis() - millis,
                            System.currentTimeMillis(),
                            DateUtils.HOUR_IN_MILLIS
                        )
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
                    setTitle(R.string.appcleaner_include_minimumage_label)
                    setView(dialogLayout.root)
                    setPositiveButton(R.string.general_save_action) { _, _ ->
                        settings.minCacheAgeMs.valueBlocking = dialogLayout.slider.value.toLong() * 60 * 1000L
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                }.show()
                true
            }
        }
    }
}