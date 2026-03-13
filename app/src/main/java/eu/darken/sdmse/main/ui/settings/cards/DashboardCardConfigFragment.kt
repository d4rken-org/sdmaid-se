package eu.darken.sdmse.main.ui.settings.cards

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.lists.differ.resetTo
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DashboardCardConfigFragmentBinding
import java.util.Collections

@AndroidEntryPoint
class DashboardCardConfigFragment : Fragment3(R.layout.dashboard_card_config_fragment) {

    override val vm: DashboardCardConfigViewModel by viewModels()
    override val ui: DashboardCardConfigFragmentBinding by viewBinding()

    private val adapter by lazy { DashboardCardConfigAdapter() }
    private var currentItems: MutableList<DashboardCardConfigAdapter.Item> = mutableListOf()
    private var isDragging = false
    private var pendingReorderConfirmation = false

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
        (ui.list.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false

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
                pendingReorderConfirmation = true
                vm.onItemsReordered(currentItems.toList())
                isDragging = false
            }

            override fun isLongPressDragEnabled(): Boolean = false
        })
        itemTouchHelper.attachToRecyclerView(ui.list)

        adapter.setOnDragStartListener { viewHolder -> itemTouchHelper.startDrag(viewHolder) }

        vm.state.observe2(ui) { state ->
            val isReorderConfirmation = pendingReorderConfirmation && state.items.hasSameOrderAs(currentItems)
            pendingReorderConfirmation = false
            currentItems = state.items.toMutableList()
            if (!isDragging) {
                if (isReorderConfirmation) {
                    // Reset AsyncListDiffer's state without move calculations
                    val animator = ui.list.itemAnimator
                    ui.list.itemAnimator = null
                    adapter.resetTo(state.items) {
                        ui.list.post { ui.list.itemAnimator = animator }
                    }
                } else {
                    adapter.update(state.items)
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun List<DashboardCardConfigAdapter.Item>.hasSameOrderAs(
        other: List<DashboardCardConfigAdapter.Item>,
    ): Boolean {
        if (size != other.size) return false
        return indices.all { this[it].stableId == other[it].stableId }
    }

    companion object {
        private val TAG = logTag("Dashboard", "CardConfig", "Fragment")
    }
}
