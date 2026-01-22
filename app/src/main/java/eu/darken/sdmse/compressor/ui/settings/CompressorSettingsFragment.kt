package eu.darken.sdmse.compressor.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.preferences.Preference2
import eu.darken.sdmse.common.ui.AgeInputDialog
import eu.darken.sdmse.common.ui.QualityInputDialog
import eu.darken.sdmse.common.ui.SizeInputDialog
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.compressor.core.CompressionEstimator
import eu.darken.sdmse.compressor.core.CompressorSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class CompressorSettingsFragment : PreferenceFragment2() {

    private val vm: CompressorSettingsViewModel by viewModels()

    @Inject lateinit var compressorSettings: CompressorSettings
    @Inject lateinit var compressionEstimator: CompressionEstimator

    override val settings: CompressorSettings by lazy { compressorSettings }
    override val preferenceFile: Int = R.xml.preferences_compressor

    private val searchLocationsPref: Preference2
        get() = findPreference("scan.location.paths")!!

    private val historyPref: Preference
        get() = findPreference("compression.history.info")!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(this) { state ->
            log(TAG) { "Updating state: $state" }
            searchLocationsPref.apply {
                summary = if (state.scanPaths.isEmpty()) {
                    getString(R.string.compressor_search_locations_default_summary)
                } else {
                    state.scanPaths.joinToString("\n") {
                        it.userReadablePath.get(requireContext())
                    }
                }
                setOnPreferenceClickListener {
                    MainDirections.goToPicker(
                        PickerRequest(
                            requestKey = searchLocationsPref.key,
                            mode = PickerRequest.PickMode.DIRS,
                            allowedAreas = setOf(
                                DataArea.Type.PORTABLE,
                                DataArea.Type.SDCARD,
                                DataArea.Type.PUBLIC_DATA,
                                DataArea.Type.PUBLIC_MEDIA
                            ),
                            selectedPaths = state.scanPaths
                        )
                    ).navigate()
                    true
                }
            }

            historyPref.summary = if (state.historyCount > 0) {
                val (formatted, _) = ByteFormatter.formatSize(requireContext(), state.historyDatabaseSize)
                getString(R.string.compressor_history_summary, state.historyCount, formatted)
            } else {
                getString(R.string.compressor_history_empty)
            }
        }
        super.onViewCreated(view, savedInstanceState)

        requireParentFragment().parentFragmentManager.setFragmentResultListener(
            searchLocationsPref.key,
            viewLifecycleOwner
        ) { requestKey, result ->
            log(TAG) { "Fragment result $requestKey=$result" }
            val pickerResult = PickerResult.fromBundle(result)
            log(TAG, INFO) { "Picker result: $pickerResult" }
            settings.scanPaths.valueBlocking = CompressorSettings.ScanPaths(
                paths = pickerResult.selectedPaths,
            )
        }
    }

    override fun onPreferencesCreated() {
        super.onPreferencesCreated()

        searchLocationsPref.setOnLongClickListener {
            vm.resetScanPaths()
            true
        }

        findPreference<Preference>(settings.minSizeBytes.keyName)?.apply {
            setOnPreferenceClickListener {
                SizeInputDialog(
                    requireActivity(),
                    titleRes = R.string.compressor_min_size_title,
                    currentSize = settings.minSizeBytes.valueBlocking,
                    onReset = { settings.minSizeBytes.valueBlocking = CompressorSettings.MIN_FILE_SIZE },
                    onSave = { settings.minSizeBytes.valueBlocking = it }
                ).show()
                true
            }
        }

        findPreference<Preference>(settings.maxAgeDays.keyName)?.apply {
            setOnPreferenceClickListener {
                val currentDays = settings.maxAgeDays.valueBlocking ?: 30
                AgeInputDialog(
                    activity = requireActivity(),
                    titleRes = R.string.compressor_max_age_title,
                    currentAge = java.time.Duration.ofDays(currentDays.toLong()),
                    onReset = { settings.maxAgeDays.valueBlocking = null },
                    onSave = { settings.maxAgeDays.valueBlocking = it.toDays().toInt() }
                ).show()
                true
            }
        }

        findPreference<Preference>(settings.compressionQuality.keyName)?.apply {
            setOnPreferenceClickListener {
                QualityInputDialog(
                    activity = requireActivity(),
                    titleRes = R.string.compressor_quality_title,
                    currentQuality = settings.compressionQuality.valueBlocking,
                    compressionEstimator = compressionEstimator,
                    onReset = { settings.compressionQuality.valueBlocking = CompressorSettings.DEFAULT_QUALITY },
                    onSave = { settings.compressionQuality.valueBlocking = it }
                ).show()
                true
            }
        }

        historyPref.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(R.string.compressor_history_clear_title)
                setMessage(R.string.compressor_history_clear_message)
                setPositiveButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
                    vm.clearHistory()
                }
                setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
            }.show()
            true
        }
    }

    companion object {
        private val TAG = logTag("Settings", "Compressor", "Fragment")
    }
}
