package eu.darken.sdmse.exclusion.ui.editor.segment

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.setChecked2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.ExclusionEditorSegmentFragmentBinding
import eu.darken.sdmse.exclusion.core.types.Exclusion


@AndroidEntryPoint
class SegmentExclusionFragment : Fragment3(R.layout.exclusion_editor_segment_fragment) {

    override val vm: SegmentExclusionViewModel by viewModels()
    override val ui: ExclusionEditorSegmentFragmentBinding by viewBinding()

    private val onBackPressedcallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            vm.cancel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedcallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.scrollView)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setNavigationOnClickListener { vm.cancel() }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_remove_exclusion -> {
                        vm.remove()
                        true
                    }

                    R.id.menu_action_save_exclusion -> {
                        vm.save()
                        true
                    }

                    else -> false
                }
            }
        }

        ui.apply {
            segmentsInput.addTextChangedListener {
                vm.updateSegments(it?.toString() ?: "")
            }
        }

        ui.optionAllowPartial.setOnClickListener { vm.toggleAllowPartial() }
        ui.optionIgnoreCasing.setOnClickListener { vm.toggleIgnoreCase() }

        var isInitial = true
        vm.state.observe2(ui) { state ->
            val exclusion = state.current
            toolbar.menu?.apply {
                findItem(R.id.menu_action_save_exclusion)?.isVisible = state.canSave
                findItem(R.id.menu_action_remove_exclusion)?.isVisible = state.canRemove
            }

            if (isInitial) {
                segmentsInput.setText(exclusion.segments.joinSegments())
                isInitial = false
            }

            segmentsDisplay.apply {
                var demo = exclusion.segments.joinSegments()
                if (exclusion.allowPartial) demo = "*$demo*"
                if (exclusion.ignoreCase) demo = demo.lowercase()
                text = demo
                isVisible = exclusion.segments.any { it.isNotEmpty() }
            }

            ui.optionAllowPartial.setChecked2(exclusion.allowPartial)
            ui.optionIgnoreCasing.setChecked2(exclusion.ignoreCase)

            ui.toolsAll.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.GENERAL)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.GENERAL) }
            }
            ui.toolsCorpsefinder.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.CORPSEFINDER)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.CORPSEFINDER) }
            }
            ui.toolsSystemcleaner.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.SYSTEMCLEANER)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.SYSTEMCLEANER) }
            }
            ui.toolsAppcleaner.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.APPCLEANER)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.APPCLEANER) }
            }
            ui.toolsDeduplicator.apply {
                isChecked = exclusion.tags.contains(Exclusion.Tag.DEDUPLICATOR)
                setOnClickListener { vm.toggleTag(Exclusion.Tag.DEDUPLICATOR) }
            }
        }

        vm.events.observe2 {
            when (it) {
                is SegmentExclusionEvents.RemoveConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.exclusion_editor_remove_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_remove_action) { _, _ ->
                        vm.remove(confirmed = true)
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                    }
                }.show()

                is SegmentExclusionEvents.UnsavedChangesConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.exclusion_editor_unsaved_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_discard_action) { _, _ ->
                        vm.cancel(confirmed = true)
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                    }
                }.show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}