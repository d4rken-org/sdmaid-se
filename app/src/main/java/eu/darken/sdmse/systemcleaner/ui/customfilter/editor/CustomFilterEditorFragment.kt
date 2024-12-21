package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SystemcleanerCustomfilterEditorFragmentBinding
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.ui.customfilter.editor.live.LiveSearchListAdapter
import kotlin.math.roundToInt


@AndroidEntryPoint
class CustomFilterEditorFragment : Fragment3(R.layout.systemcleaner_customfilter_editor_fragment) {

    override val vm: CustomFilterEditorViewModel by viewModels()
    override val ui: SystemcleanerCustomfilterEditorFragmentBinding by viewBinding()

    private lateinit var liveSearchBehavior: BottomSheetBehavior<LinearLayout>

    private val onBackPressedcallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (liveSearchBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
                liveSearchBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                return
            }
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
            bottomHalf(ui.liveSearchResults)
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

        ui.labelInput.addTextChangedListener { text: Editable? ->
            vm.updateLabel(text?.toString() ?: "")
        }

        ui.pathInput.apply {
            type = TaggedInputView.Type.SEGMENTS
            onUserAddedTag = { tag -> vm.addPath(tag as SegmentCriterium) }
            onUserRemovedTag = { tag -> vm.removePath(tag as SegmentCriterium) }
            onFocusChange = { _, hasFocus -> if (hasFocus) closeLiveSearch() }
        }

        ui.nameInput.apply {
            type = TaggedInputView.Type.NAME
            onUserAddedTag = { tag -> vm.addNameContains(tag as NameCriterium) }
            onUserRemovedTag = { tag -> vm.removeNameContains(tag as NameCriterium) }
            onFocusChange = { _, hasFocus -> if (hasFocus) closeLiveSearch() }

        }

        ui.exclusionsInput.apply {
            type = TaggedInputView.Type.SEGMENTS
            onUserAddedTag = { tag -> vm.addExclusion(tag as SegmentCriterium) }
            onUserRemovedTag = { tag -> vm.removeExclusion(tag as SegmentCriterium) }
            onFocusChange = { _, hasFocus -> if (hasFocus) closeLiveSearch() }
        }

        val areaChips = mutableMapOf<DataArea.Type, Chip>()
        setOf(
            DataArea.Type.SDCARD,
            DataArea.Type.PUBLIC_DATA,
            DataArea.Type.PUBLIC_MEDIA,
            DataArea.Type.PUBLIC_OBB,
            DataArea.Type.PRIVATE_DATA,
            DataArea.Type.PORTABLE,
        ).forEach { type ->
            val chip = Chip(
                context,
                null,
                com.google.android.material.R.style.Widget_Material3_Chip_Filter_Elevated
            ).apply {
                id = ViewCompat.generateViewId()
                this.text = type.raw
                isClickable = true
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked -> vm.toggleArea(type, isChecked) }
                areaChips[type] = this
            }
            ui.dataAreasContainer.addView(chip)
        }

        ui.apply {
            filetypesOptionFiles.setOnClickListener { vm.toggleFileType(FileType.FILE) }
            filetypesOptionDirectories.setOnClickListener { vm.toggleFileType(FileType.DIRECTORY) }
        }

        vm.state.observe2(ui) { state ->
            val config = state.current
            toolbar.menu?.apply {
                findItem(R.id.menu_action_save_exclusion)?.isVisible = state.canSave
                findItem(R.id.menu_action_remove_exclusion)?.isVisible = state.canRemove
            }
            toolbar.subtitle = config.label
            if (labelInput.text.isNullOrEmpty()) labelInput.setText(config.label)

            pathInput.setTags(
                config.pathCriteria?.toList() ?: emptyList()
            )
            nameInput.setTags(
                config.nameCriteria?.toList() ?: emptyList()
            )
            exclusionsInput.setTags(
                config.exclusionCriteria?.toList() ?: emptyList()
            )

            areaChips.entries.forEach { (type, chip) ->
                chip.isChecked = config.areas?.contains(type) == true
            }

            filetypesOptionFiles.isChecked = config.fileTypes?.contains(FileType.FILE) == true
            filetypesOptionDirectories.isChecked = config.fileTypes?.contains(FileType.DIRECTORY) == true
        }

        vm.events.observe2 {
            when (it) {
                is CustomFilterEditorEvents.RemoveConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.systemcleaner_editor_remove_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_remove_action) { _, _ ->
                        vm.remove(confirmed = true)
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                    }
                }.show()

                is CustomFilterEditorEvents.UnsavedChangesConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.systemcleaner_editor_unsaved_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_discard_action) { _, _ ->
                        vm.cancel(confirmed = true)
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                    }
                }.show()
            }
        }

        // Initialize the bottom sheet behavior
        liveSearchBehavior = BottomSheetBehavior.from(ui.liveSearchContainer).apply {
            isHideable = false
            state = BottomSheetBehavior.STATE_COLLAPSED
            peekHeight = requireContext().dpToPx(64f)
            ui.root.post {
                maxHeight = ((ui.root.height - ui.toolbar.height) * 0.7f).roundToInt()
            }
        }

        val liveSearchAdapter = LiveSearchListAdapter().apply {
            ui.liveSearchResults.setupDefaults(this, verticalDividers = false)
        }

        vm.liveSearch.observe2(ui) { state ->
            liveSearchPrimary.text = when (state.firstInit) {
                true -> getString(R.string.systemcleaner_customfilter_editor_livesearch_label)
                false -> getQuantityString2(
                    eu.darken.sdmse.common.R.plurals.result_x_items,
                    state.matches.size
                )
            }
            liveSearchSecondary.apply {
                text = when {
                    state.firstInit -> getString(eu.darken.sdmse.common.R.string.general_progress_ready)
                    state.progress == null -> getString(eu.darken.sdmse.common.R.string.general_progress_done)
                    else -> state.progress.primary.get(requireContext())
                }
                isGone = text.isEmpty()
            }

            liveSearchProgress.isGone = state.progress == null

            liveSearchAdapter.update(state.matches)

            liveSearchBehavior.apply {
                isDraggable = !state.firstInit
                peekHeight = when {
                    state.progress != null -> requireContext().dpToPx(96f)
                    state.matches.isNotEmpty() -> requireContext().dpToPx(128f)
                    else -> requireContext().dpToPx(64f)
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun closeLiveSearch() {
        liveSearchBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
}