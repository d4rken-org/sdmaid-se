package eu.darken.sdmse.deduplicator.ui.settings.arbiter

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.doNavigate
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DeduplicatorArbiterConfigFragmentBinding
import java.util.Collections

@AndroidEntryPoint
class ArbiterConfigFragment : Fragment3(R.layout.deduplicator_arbiter_config_fragment) {

    override val vm: ArbiterConfigViewModel by viewModels()
    override val ui: DeduplicatorArbiterConfigFragmentBinding by viewBinding()

    private val adapter by lazy { ArbiterConfigAdapter() }
    private var currentItems: MutableList<ArbiterConfigAdapter.Item> = mutableListOf()
    private var isDragging = false
    private var pendingReorderUpdate = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.list, bottom = true)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_reset -> {
                        vm.resetToDefaults()
                        true
                    }

                    else -> false
                }
            }
        }

        ui.list.setupDefaults(adapter, verticalDividers = false)

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false
                }
                // Don't allow moving to position 0 (header)
                if (toPos == 0) return false

                Collections.swap(currentItems, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                // Don't allow dropping over the header
                return target.bindingAdapterPosition != 0
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                pendingReorderUpdate = true
                vm.onItemsReordered(currentItems.toList())
                isDragging = false
            }

            override fun isLongPressDragEnabled(): Boolean = false
        })
        itemTouchHelper.attachToRecyclerView(ui.list)

        adapter.setOnDragStartListener { viewHolder -> itemTouchHelper.startDrag(viewHolder) }

        vm.state.observe2(ui) { state ->
            currentItems = state.items.toMutableList()
            if (!isDragging) {
                if (pendingReorderUpdate) {
                    // Skip adapter update - visual state is already correct from drag
                    pendingReorderUpdate = false
                } else {
                    adapter.update(state.items)
                }
            }
        }

        vm.modeSelectionEvents.observe2 { event -> showModeSelectionDialog(event) }

        vm.pickerEvents.observe2 { request -> doNavigate(MainDirections.goToPicker(request)) }

        super.onViewCreated(view, savedInstanceState)

        parentFragmentManager.setFragmentResultListener(
            ArbiterConfigViewModel.PICKER_REQUEST_KEY,
            viewLifecycleOwner,
        ) { requestKey, result ->
            log(TAG) { "Fragment result $requestKey=$result" }
            val pickerResult = PickerResult.fromBundle(result)
            log(TAG, INFO) { "Picker result: $pickerResult" }
            vm.updatePreferredPaths(pickerResult.selectedPaths)
        }
    }

    private fun showModeSelectionDialog(event: ArbiterConfigViewModel.ModeSelectionEvent) {
        val modes = event.modes
        val currentMode = event.item.criterium.criteriumMode()
        val labels = modes.map { getString(it.labelRes) }.toTypedArray()
        val currentIndex = modes.indexOf(currentMode).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(R.string.deduplicator_arbiter_select_mode_title)
            setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                vm.onModeSelected(event.item, modes[which])
                dialog.dismiss()
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action, null)
        }.show()
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Settings", "Arbiter", "Fragment")
    }
}
