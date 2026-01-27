package eu.darken.sdmse.compressor.ui.setup

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.ui.AgeInputDialog
import eu.darken.sdmse.common.ui.SizeInputDialog
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.compressor.core.CompressionEstimator
import eu.darken.sdmse.compressor.core.CompressorSettings
import eu.darken.sdmse.compressor.ui.onboarding.CompressorOnboardingDialog
import eu.darken.sdmse.databinding.CompressorSetupFragmentBinding
import java.time.Duration
import javax.inject.Inject

@AndroidEntryPoint
class CompressorSetupFragment : Fragment3(R.layout.compressor_setup_fragment) {

    override val vm: CompressorSetupViewModel by viewModels()
    override val ui: CompressorSetupFragmentBinding by viewBinding()

    @Inject lateinit var compressionEstimator: CompressionEstimator
    @Inject lateinit var onboardingDialog: CompressorOnboardingDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.scrollview, bottom = true)
        }

        ui.toolbar.setupWithNavController(findNavController())

        ui.pathsChangeAction.setOnClickListener {
            vm.openPathPicker()
        }

        ui.qualitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                vm.updateQuality(value.toInt())
            }
        }

        ui.ageCard.setOnClickListener {
            val currentDays = vm.state.value?.minAgeDays ?: 30
            AgeInputDialog(
                activity = requireActivity(),
                titleRes = R.string.compressor_min_age_title,
                currentAge = Duration.ofDays(currentDays.toLong()),
                onReset = { vm.updateMinAge(null) },
                onSave = { vm.updateMinAge(it.toDays().toInt()) }
            ).show()
        }

        ui.minSizeCard.setOnClickListener {
            val currentSize = vm.state.value?.minSizeBytes ?: CompressorSettings.MIN_FILE_SIZE
            SizeInputDialog(
                activity = requireActivity(),
                titleRes = R.string.compressor_min_size_title,
                currentSize = currentSize,
                onReset = { vm.updateMinSize(CompressorSettings.MIN_FILE_SIZE) },
                onSave = { vm.updateMinSize(it) }
            ).show()
        }

        ui.qualityExampleAction.setOnClickListener {
            vm.showExample()
        }

        ui.startScanAction.setOnClickListener {
            vm.startScan()
        }

        vm.state.observe2(ui) { state ->
            scrollview.isInvisible = state.progress != null
            loadingOverlay.setProgress(state.progress)

            pathsValue.text = if (state.scanPaths.isEmpty()) {
                getString(R.string.compressor_setup_paths_default)
            } else {
                state.scanPaths.joinToString("\n") {
                    it.userReadablePath.get(requireContext())
                }
            }

            qualitySlider.value = state.quality.toFloat()
            qualityValue.text = getString(R.string.compressor_quality_value, state.quality)
            qualityHint.text = getQualityHint(state.quality)

            qualityEstimate.text = state.estimatedSavingsPercent?.let {
                getString(R.string.compressor_estimated_savings_percent, it)
            } ?: ""
            qualityEstimate.isVisible = state.estimatedSavingsPercent != null && state.quality < 100

            ageValue.text = if (state.minAgeDays == null) {
                getString(R.string.compressor_setup_age_none)
            } else {
                resources.getQuantityString(
                    R.plurals.compressor_min_age_x_days,
                    state.minAgeDays,
                    state.minAgeDays
                )
            }

            minSizeValue.text = Formatter.formatShortFileSize(requireContext(), state.minSizeBytes)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is CompressorSetupEvents.OpenPathPicker -> {
                    MainDirections.goToPicker(
                        PickerRequest(
                            requestKey = PICKER_REQUEST_KEY,
                            mode = PickerRequest.PickMode.DIRS,
                            allowedAreas = setOf(
                                DataArea.Type.PORTABLE,
                                DataArea.Type.SDCARD,
                                DataArea.Type.PUBLIC_DATA,
                                DataArea.Type.PUBLIC_MEDIA
                            ),
                            selectedPaths = event.currentPaths.toList()
                        )
                    ).navigate()
                }

                is CompressorSetupEvents.NavigateToList -> {
                    CompressorSetupFragmentDirections.actionCompressorSetupFragmentToCompressorListFragment()
                        .navigate()
                }

                is CompressorSetupEvents.ShowExample -> {
                    onboardingDialog.show(
                        sampleImage = event.sampleImage,
                        quality = event.quality,
                        onDismiss = { }
                    )
                }

                is CompressorSetupEvents.NoExampleFound -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.compressor_no_example_found,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        parentFragmentManager.setFragmentResultListener(
            PICKER_REQUEST_KEY,
            viewLifecycleOwner
        ) { requestKey, result ->
            log(TAG) { "Fragment result $requestKey=$result" }
            val pickerResult = PickerResult.fromBundle(result)
            log(TAG, INFO) { "Picker result: $pickerResult" }
            vm.updatePaths(pickerResult.selectedPaths)
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun getQualityHint(quality: Int): String = when {
        quality <= 50 -> getString(R.string.compressor_quality_hint_low)
        quality >= 95 -> getString(R.string.compressor_quality_hint_high)
        else -> getString(R.string.compressor_quality_hint_normal)
    }

    companion object {
        private const val PICKER_REQUEST_KEY = "compressor.setup.paths"
        private val TAG = logTag("Compressor", "Setup", "Fragment")
    }
}
