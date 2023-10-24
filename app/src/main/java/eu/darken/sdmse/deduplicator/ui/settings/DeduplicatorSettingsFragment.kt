package eu.darken.sdmse.deduplicator.ui.settings

import android.text.format.Formatter
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.databinding.ViewPreferenceSeekbarBinding
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class DeduplicatorSettingsFragment : PreferenceFragment2() {

    private val vm: DeduplicatorSettingsViewModel by viewModels()

    @Inject lateinit var ddSettings: DeduplicatorSettings

    override val settings: DeduplicatorSettings by lazy { ddSettings }
    override val preferenceFile: Int = R.xml.preferences_deduplicator

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        findPreference<Preference>(settings.minSizeBytes.keyName)?.apply {
            setOnPreferenceClickListener {
                val dialogLayout = ViewPreferenceSeekbarBinding.inflate(layoutInflater, null, false)
                dialogLayout.apply {
                    slider.valueFrom = 0f
                    slider.valueTo = 100 * 1024f
                    slider.value = (settings.minSizeBytes.valueBlocking / 1024f).coerceAtMost(slider.valueTo)

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
                    setTitle(R.string.deduplicator_skip_minsize_title)
                    setView(dialogLayout.root)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
                        settings.minSizeBytes.valueBlocking = dialogLayout.slider.value.toLong() * 1024L
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
                        settings.minSizeBytes.valueBlocking = DeduplicatorSettings.MIN_FILE_SIZE
                    }
                }.show()
                true
            }
        }
    }

}