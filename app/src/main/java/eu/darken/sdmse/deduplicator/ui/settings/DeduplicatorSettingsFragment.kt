package eu.darken.sdmse.deduplicator.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.preferences.Preference2
import eu.darken.sdmse.common.ui.SizeInputDialog
import eu.darken.sdmse.common.uix.PreferenceFragment2
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class DeduplicatorSettingsFragment : PreferenceFragment2() {

    private val vm: DeduplicatorSettingsViewModel by viewModels()

    @Inject lateinit var ddSettings: DeduplicatorSettings

    override val settings: DeduplicatorSettings by lazy { ddSettings }
    override val preferenceFile: Int = R.xml.preferences_deduplicator

    private val searchLocationsPref: Preference2
        get() = findPreference("scan.location.paths")!!

    private val keepPreferPathsPref: Preference2
        get() = findPreference("arbiter.keep.prefer.paths")!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.state.observe2(this) { state ->
            log(TAG) { "Updating state: $state" }
            searchLocationsPref.apply {
                summary = if (state.scanPaths.isEmpty()) {
                    getString(R.string.deduplicator_search_locations_all_summary)
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
            keepPreferPathsPref.apply {
                summary = if (state.keepPreferPaths.isEmpty()) {
                    getString(R.string.deduplicator_keep_prefer_paths_none_summary)
                } else {
                    state.keepPreferPaths.joinToString("\n") {
                        it.userReadablePath.get(requireContext())
                    }
                }
                setOnPreferenceClickListener {
                    MainDirections.goToPicker(
                        PickerRequest(
                            requestKey = keepPreferPathsPref.key,
                            mode = PickerRequest.PickMode.DIRS,
                            allowedAreas = setOf(
                                DataArea.Type.PORTABLE,
                                DataArea.Type.SDCARD,
                                DataArea.Type.PUBLIC_DATA,
                                DataArea.Type.PUBLIC_MEDIA
                            ),
                            selectedPaths = state.keepPreferPaths
                        )
                    ).navigate()
                    true
                }
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
            settings.scanPaths.valueBlocking = DeduplicatorSettings.ScanPaths(
                paths = pickerResult.selectedPaths,
            )
        }

        requireParentFragment().parentFragmentManager.setFragmentResultListener(
            keepPreferPathsPref.key,
            viewLifecycleOwner
        ) { requestKey, result ->
            log(TAG) { "Fragment result $requestKey=$result" }
            val pickerResult = PickerResult.fromBundle(result)
            log(TAG, INFO) { "Picker result: $pickerResult" }
            settings.keepPreferPaths.valueBlocking = DeduplicatorSettings.KeepPreferPaths(
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

        keepPreferPathsPref.setOnLongClickListener {
            vm.resetKeepPreferPaths()
            true
        }

        findPreference<Preference>(settings.minSizeBytes.keyName)?.apply {
            setOnPreferenceClickListener {
                SizeInputDialog(
                    requireActivity(),
                    titleRes = R.string.deduplicator_skip_minsize_title,
                    currentSize = settings.minSizeBytes.valueBlocking,
                    onReset = { settings.minSizeBytes.valueBlocking = DeduplicatorSettings.MIN_FILE_SIZE },
                    onSave = { settings.minSizeBytes.valueBlocking = it }
                ).show()
                true
            }
        }
    }

    companion object {
        private val TAG = logTag("Settings", "Deduplicator", "Fragment")
    }
}