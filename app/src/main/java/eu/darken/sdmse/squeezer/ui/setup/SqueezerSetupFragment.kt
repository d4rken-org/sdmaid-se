package eu.darken.sdmse.squeezer.ui.setup

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
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.ui.AgeInputDialog
import eu.darken.sdmse.common.ui.SizeInputDialog
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.squeezer.core.CompressionEstimator
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.ui.onboarding.SqueezerOnboardingDialog
import eu.darken.sdmse.databinding.SqueezerSetupFragmentBinding
import java.time.Duration
import javax.inject.Inject

@AndroidEntryPoint
class SqueezerSetupFragment : Fragment3(R.layout.squeezer_setup_fragment) {

    override val vm: SqueezerSetupViewModel by viewModels()
    override val ui: SqueezerSetupFragmentBinding by viewBinding()

    @Inject lateinit var compressionEstimator: CompressionEstimator
    @Inject lateinit var onboardingDialog: SqueezerOnboardingDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.scrollview, bottom = true)
        }

        ui.toolbar.setupWithNavController(findNavController())

        ui.pathsCard.setOnClickListener {
            vm.openPathPicker()
        }

        if (BuildConfigWrap.DEBUG) ui.qualitySlider.valueFrom = 1f

        ui.qualitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                vm.updateQuality(value.toInt())
            }
        }

        ui.ageCard.setOnClickListener {
            val currentAge = vm.state.value?.minAge ?: SqueezerSettings.MIN_AGE_DEFAULT
            AgeInputDialog(
                activity = requireActivity(),
                titleRes = R.string.squeezer_min_age_title,
                maximumAge = Duration.ofDays(365),
                currentAge = currentAge,
                onReset = { vm.updateMinAge(SqueezerSettings.MIN_AGE_DEFAULT) },
                onSave = { vm.updateMinAge(it) }
            ).show()
        }

        ui.minSizeCard.setOnClickListener {
            val currentSize = vm.state.value?.minSizeBytes ?: SqueezerSettings.MIN_FILE_SIZE
            SizeInputDialog(
                activity = requireActivity(),
                titleRes = R.string.squeezer_min_size_title,
                currentSize = currentSize,
                maximumSize = 20 * 1000 * 1000L,
                onReset = { vm.updateMinSize(SqueezerSettings.MIN_FILE_SIZE) },
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
                getString(R.string.squeezer_setup_paths_default)
            } else {
                state.scanPaths.joinToString("\n") {
                    it.userReadablePath.get(requireContext())
                }
            }

            qualitySlider.value = state.quality.toFloat()
            qualityValue.text = getString(R.string.squeezer_quality_value, state.quality)
            qualityHint.text = getQualityHint(state.quality)
            qualityHint.setTextColor(
                requireContext().getColorForAttr(
                    if (state.quality < 40) {
                        androidx.appcompat.R.attr.colorError
                    } else {
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    }
                )
            )

            qualityEstimate.text = state.estimatedSavingsPercent?.let {
                getString(R.string.squeezer_estimated_savings_percent, it)
            } ?: ""
            qualityEstimate.isVisible = state.estimatedSavingsPercent != null && state.quality < 100

            val minAgeDays = state.minAge.toDays().toInt()
            ageValue.text = resources.getQuantityString(
                R.plurals.squeezer_min_age_x_days,
                minAgeDays,
                minAgeDays
            )

            minSizeValue.text = Formatter.formatShortFileSize(requireContext(), state.minSizeBytes)

            qualityExampleAction.isInvisible = state.isLoadingExample
            qualityExampleLoading.isVisible = state.isLoadingExample

            startScanAction.isEnabled = state.canStartScan
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is SqueezerSetupEvents.OpenPathPicker -> {
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

                is SqueezerSetupEvents.NavigateToList -> {
                    SqueezerSetupFragmentDirections.actionSqueezerSetupFragmentToSqueezerListFragment()
                        .navigate()
                }

                is SqueezerSetupEvents.ShowExample -> {
                    onboardingDialog.show(
                        sampleImage = event.sampleImage,
                        quality = event.quality,
                        onDismiss = { }
                    )
                }

                is SqueezerSetupEvents.NoExampleFound -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.squeezer_no_example_found,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is SqueezerSetupEvents.NoResultsFound -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.squeezer_result_empty_message,
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
        quality < 40 -> getString(R.string.squeezer_quality_hint_very_low)
        quality <= 50 -> getString(R.string.squeezer_quality_hint_low)
        quality >= 95 -> getString(R.string.squeezer_quality_hint_high)
        else -> getString(R.string.squeezer_quality_hint_normal)
    }

    companion object {
        private const val PICKER_REQUEST_KEY = "squeezer.setup.paths"
        private val TAG = logTag("Squeezer", "Setup", "Fragment")
    }
}
