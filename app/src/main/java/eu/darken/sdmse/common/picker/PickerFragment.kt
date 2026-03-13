package eu.darken.sdmse.common.picker

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.iterator
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.dpToPx
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getSpanCount
import eu.darken.sdmse.common.navigation.popBackStack
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.CommonPickerFragmentBinding
import kotlin.math.roundToInt

@AndroidEntryPoint
class PickerFragment : Fragment3(R.layout.common_picker_fragment) {

    override val vm: PickerViewModel by viewModels()
    override val ui: CommonPickerFragmentBinding by viewBinding()

    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    private var initialSheetCollapse = true

    private val onBackPressedcallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            vm.goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedcallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.loadingOverlay, bottom = true)
        }

        var navBarInsets = 0
        ViewCompat.setOnApplyWindowInsetsListener(ui.list) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            navBarInsets = systemBars.bottom
            insets
        }

        sheetBehavior = BottomSheetBehavior.from(ui.selectedContainer).apply {
            isHideable = false
            state = BottomSheetBehavior.STATE_COLLAPSED
            peekHeight = requireContext().dpToPx(64f)

            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {}

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val sheetHeight = ui.root.height - bottomSheet.top
                    ui.list.updatePadding(bottom = sheetHeight + navBarInsets)
                }
            })
        }

        fun updateSheetDimensions() {
            val newMaxHeight = ((ui.root.height - ui.toolbar.height) * 0.5f).roundToInt()
            sheetBehavior.maxHeight = newMaxHeight

            val sheetHeight = ui.root.height - ui.selectedContainer.top
            ui.list.updatePadding(bottom = sheetHeight.coerceAtLeast(0) + navBarInsets)
        }

        ui.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateSheetDimensions()
        }

        ui.root.doOnLayout {
            updateSheetDimensions()
        }

        ui.toolbar.apply {
            setNavigationOnClickListener { vm.goBack() }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_save -> {
                        vm.save()
                        true
                    }

                    R.id.menu_action_home -> {
                        vm.home()
                        true
                    }

                    R.id.menu_action_select_all -> {
                        vm.selectAll()
                        true
                    }

                    else -> false
                }
            }
        }

        val pickerAdapter = PickerAdapter()
        ui.list.setupDefaults(
            pickerAdapter,
            horizontalDividers = true,
            layouter = GridLayoutManager(context, getSpanCount(), GridLayoutManager.VERTICAL, false),
        )
        val selectedAdapter = PickerSelectedAdapter()
        ui.selectedList.setupDefaults(
            selectedAdapter,
            horizontalDividers = true,
        )
        ui.selectedSecondary.text = requireContext().getQuantityString2(R.plurals.picker_selected_paths_subtitle, 0)

        vm.state.observe2(ui) { state ->
            log(TAG) { "updating with new state: $state" }
            toolbar.subtitle = state.current?.lookup?.path ?: ""
            toolbar.menu.iterator().forEach { it.isVisible = state.progress == null }

            toolbar.setNavigationIcon(
                if (state.current == null) {
                    R.drawable.ic_baseline_close_24
                } else {
                    R.drawable.ic_baseline_arrow_back_24
                }
            )

            if (state.progress == null) pickerAdapter.update(state.items)

            selectedSecondary.text = requireContext().getQuantityString2(
                R.plurals.picker_selected_paths_subtitle,
                state.selected.size,
            )
            if (state.progress == null) selectedAdapter.update(state.selected)

            loadingOverlay.setProgress(state.progress)

            if (state.selected.isNotEmpty() && initialSheetCollapse && sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                sheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                initialSheetCollapse = false
            }
        }

        vm.events.observe2 { event ->
            when (event) {
                PickerEvent.ExitConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(R.string.picker_unsaved_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_discard_action) { _, _ ->
                        vm.cancel(confirmed = true)
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                }.show()

                is PickerEvent.Save -> {
                    setFragmentResult(event.requestKey, event.result.toBundle())
                    popBackStack()
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Common", "Picker", "Fragment")
    }
}
