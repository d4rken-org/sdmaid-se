package eu.darken.sdmse.swiper.ui.status

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.ViewCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SwiperStatusFragmentBinding
import eu.darken.sdmse.swiper.core.SwipeDecision
import kotlin.math.abs

@AndroidEntryPoint
class SwiperStatusFragment : Fragment3(R.layout.swiper_status_fragment) {

    override val vm: SwiperStatusViewModel by viewModels()
    override val ui: SwiperStatusFragmentBinding by viewBinding()

    private var currentState: SwiperStatusViewModel.State? = null
    private var isToolbarCollapsed = false
    private var menuFinalizeAction: MenuItem? = null
    private var offsetListener: AppBarLayout.OnOffsetChangedListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbar, top = true)
            insetsPadding(ui.list, bottom = true)
        }

        ui.toolbar.setupWithNavController(findNavController())

        menuFinalizeAction = ui.toolbar.menu.findItem(R.id.action_finalize)

        // Hide until state loads with correct dynamic labels
        ui.finalizeAction.visibility = View.INVISIBLE
        menuFinalizeAction?.isVisible = false

        ui.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_finalize -> {
                    showDeleteConfirmation()
                    true
                }
                else -> false
            }
        }

        offsetListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            if (view == null) return@OnOffsetChangedListener

            val totalScrollRange = appBarLayout.totalScrollRange
            if (totalScrollRange == 0) return@OnOffsetChangedListener

            val collapseRatio = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()
            val newCollapsed = collapseRatio >= 0.7f

            if (newCollapsed != isToolbarCollapsed) {
                isToolbarCollapsed = newCollapsed
                updateCollapseVisibility()
            }

            // Fade expanded content (fully faded at 50% collapse)
            ui.expandedContent.alpha = 1f - (collapseRatio / 0.5f).coerceIn(0f, 1f)
        }
        ui.appbar.addOnOffsetChangedListener(offsetListener)

        val adapter = SwiperStatusAdapter()
        ui.list.setupDefaults(adapter, verticalDividers = false)

        adapter.onItemClick = { item -> vm.navigateToItem(item.id) }
        adapter.onResetClick = { item -> vm.resetDecision(item.id) }
        adapter.onQuickKeepClick = { item -> vm.markKeep(item.id) }
        adapter.onQuickDeleteClick = { item -> vm.markDelete(item.id) }

        installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_swiper_status_cab,
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<SwiperStatusAdapter.Item> ->
                when (item.itemId) {
                    R.id.action_keep_selected -> {
                        vm.updateDecisions(selected.map { it.swipeItem }, SwipeDecision.KEEP)
                        tracker.clearSelection()
                        true
                    }
                    R.id.action_delete_selected -> {
                        vm.updateDecisions(selected.map { it.swipeItem }, SwipeDecision.DELETE)
                        tracker.clearSelection()
                        true
                    }
                    R.id.action_reset_selected -> {
                        vm.updateDecisions(selected.map { it.swipeItem }, SwipeDecision.UNDECIDED)
                        tracker.clearSelection()
                        true
                    }
                    R.id.action_exclude_selected -> {
                        vm.excludeAndRemove(selected.map { it.swipeItem })
                        tracker.clearSelection()
                        true
                    }
                    else -> false
                }
            }
        )

        ui.finalizeAction.setOnClickListener { showDeleteConfirmation() }

        ui.undecidedRow.setOnClickListener { scrollToNextUndecided(adapter) }

        vm.events.observe2(ui) { event ->
            when (event) {
                SwiperStatusEvents.NavigateToSessions -> {
                    findNavController().popBackStack(R.id.swiperSessionsFragment, inclusive = false)
                }
            }
        }

        vm.state.observe2(ui) { state: SwiperStatusViewModel.State ->
            currentState = state

            // Keep stats
            keepCount.text = resources.getQuantityString(
                R.plurals.swiper_session_status_to_keep,
                state.keepCount,
                state.keepCount
            )
            val (keepSizeFormatted, _) = ByteFormatter.formatSize(requireContext(), state.keepSize)
            keepSize.text = "($keepSizeFormatted)"

            // Delete stats
            deleteCount.text = resources.getQuantityString(
                R.plurals.swiper_session_status_to_delete,
                state.deleteCount,
                state.deleteCount
            )
            val (deleteSizeFormatted, _) = ByteFormatter.formatSize(requireContext(), state.deleteSize)
            deleteSize.text = "($deleteSizeFormatted)"

            // Undecided stats
            undecidedCount.text = resources.getQuantityString(
                R.plurals.swiper_session_status_undecided,
                state.undecidedCount,
                state.undecidedCount
            )
            val (undecidedSizeFormatted, _) = ByteFormatter.formatSize(requireContext(), state.undecidedSize)
            undecidedSize.text = "($undecidedSizeFormatted)"

            // Hide undecided row when all items are decided
            undecidedRow.visibility =
                if (state.undecidedCount > 0) View.VISIBLE else View.GONE

            // Already kept (inline in keep row)
            val showKept = state.alreadyKeptCount > 0
            keepRowSeparator.visibility = if (showKept) View.VISIBLE else View.GONE
            alreadyKeptIcon.visibility = if (showKept) View.VISIBLE else View.GONE
            alreadyKeptCount.visibility = if (showKept) View.VISIBLE else View.GONE
            alreadyKeptCount.text = resources.getQuantityString(
                R.plurals.swiper_session_status_kept,
                state.alreadyKeptCount,
                state.alreadyKeptCount,
            )

            // Already deleted (inline in delete row)
            val showDeleted = state.alreadyDeletedCount > 0
            deleteRowSeparator.visibility = if (showDeleted) View.VISIBLE else View.GONE
            alreadyDeletedIcon.visibility = if (showDeleted) View.VISIBLE else View.GONE
            alreadyDeletedCount.visibility = if (showDeleted) View.VISIBLE else View.GONE
            alreadyDeletedCount.text = resources.getQuantityString(
                R.plurals.swiper_session_status_deleted,
                state.alreadyDeletedCount,
                state.alreadyDeletedCount,
            )

            adapter.update(state.items.map { SwiperStatusAdapter.Item(it) })

            // Update button and menu item state
            updateFinalizeState(state)
            updateCollapseVisibility()
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        ui.appbar.removeOnOffsetChangedListener(offsetListener)
        offsetListener = null
        super.onDestroyView()
    }

    private fun updateFinalizeState(state: SwiperStatusViewModel.State) {
        data class ButtonState(
            val text: String,
            val enabled: Boolean,
            val icon: Int,
            val visible: Boolean,
            val useErrorColor: Boolean,
        )

        val buttonState = when {
            // Deletions complete, no more to delete - show done
            state.deletedCount > 0 && state.deleteCount == 0 -> ButtonState(
                text = getString(eu.darken.sdmse.common.R.string.general_done_action),
                enabled = state.canDone,
                icon = R.drawable.ic_heart,
                visible = true,
                useErrorColor = false,
            )
            // Has items to delete - show delete (RED)
            state.deleteCount > 0 -> ButtonState(
                text = if (state.undecidedCount > 0) {
                    getString(R.string.swiper_delete_x_action, state.deleteCount)
                } else {
                    getString(eu.darken.sdmse.common.R.string.general_delete_action)
                },
                enabled = state.canFinalize,
                icon = R.drawable.ic_baseline_delete_forever_24,
                visible = true,
                useErrorColor = true,
            )
            // Only keep items - show apply
            state.keepCount > 0 -> ButtonState(
                text = getString(eu.darken.sdmse.common.R.string.general_apply_action),
                enabled = state.canFinalize,
                icon = R.drawable.ic_done_24,
                visible = true,
                useErrorColor = false,
            )
            // Nothing to do - hide button
            else -> ButtonState(
                text = "",
                enabled = false,
                icon = R.drawable.ic_done_24,
                visible = false,
                useErrorColor = false,
            )
        }

        // Update button visibility
        ui.finalizeAction.visibility = if (buttonState.visible) View.VISIBLE else View.GONE

        if (buttonState.visible) {
            ui.finalizeAction.text = buttonState.text
            ui.finalizeAction.isEnabled = buttonState.enabled
            ui.finalizeAction.setIconResource(buttonState.icon)

            // Apply error color for delete action
            val context = requireContext()
            if (buttonState.useErrorColor) {
                ViewCompat.setBackgroundTintList(
                    ui.finalizeAction,
                    ColorStateList.valueOf(context.getColorForAttr(androidx.appcompat.R.attr.colorError))
                )
                ui.finalizeAction.setTextColor(context.getColorForAttr(com.google.android.material.R.attr.colorOnError))
                ui.finalizeAction.iconTint = ColorStateList.valueOf(
                    context.getColorForAttr(com.google.android.material.R.attr.colorOnError)
                )
            } else {
                // Reset to default tonal button colors
                ViewCompat.setBackgroundTintList(
                    ui.finalizeAction,
                    ColorStateList.valueOf(context.getColorForAttr(com.google.android.material.R.attr.colorSecondaryContainer))
                )
                ui.finalizeAction.setTextColor(context.getColorForAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
                ui.finalizeAction.iconTint = ColorStateList.valueOf(
                    context.getColorForAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                )
            }
        }

        // Update menu item
        menuFinalizeAction?.apply {
            isVisible = isToolbarCollapsed && buttonState.visible
            title = buttonState.text
            isEnabled = buttonState.enabled
            setIcon(buttonState.icon)
        }
    }

    private fun updateCollapseVisibility() {
        val state = currentState
        val buttonVisible = state != null && (state.deletedCount > 0 || state.deleteCount > 0 || state.keepCount > 0)
        menuFinalizeAction?.isVisible = isToolbarCollapsed && buttonVisible
        ui.toolbar.subtitle = if (isToolbarCollapsed) formatCollapsedSubtitle() else null
    }

    private fun formatCollapsedSubtitle(): String? {
        val state = currentState ?: return null

        val (deleteSizeFormatted, _) = ByteFormatter.formatSize(requireContext(), state.deleteSize)

        return buildString {
            append(getString(R.string.swiper_keep_action))
            append(" ")
            append(state.keepCount)
            append(" • ")
            append(getString(eu.darken.sdmse.common.R.string.general_delete_action))
            append(" ")
            append(state.deleteCount)
            append(" • ")
            append(deleteSizeFormatted)
        }
    }

    private fun scrollToNextUndecided(adapter: SwiperStatusAdapter) {
        val firstUndecidedIndex = adapter.data.indexOfFirst {
            it.swipeItem.decision == SwipeDecision.UNDECIDED
        }
        if (firstUndecidedIndex >= 0) {
            ui.list.smoothScrollToPosition(firstUndecidedIndex)
        }
    }

    private fun showDeleteConfirmation() {
        val state = currentState ?: return

        // If all items are deleted, just finish
        if (state.deletedCount > 0 && state.deleteCount == 0) {
            vm.done()
            return
        }

        // If no items marked for deletion, just finalize
        if (state.deleteCount == 0) {
            vm.finalize()
            return
        }

        // Show confirmation for delete items
        val (sizeFormatted, _) = ByteFormatter.formatSize(requireContext(), state.deleteSize)

        val deleteMessage = resources.getQuantityString(
            R.plurals.swiper_delete_confirmation_message,
            state.deleteCount,
            state.deleteCount,
            sizeFormatted,
        )
        val message = if (state.undecidedCount > 0) {
            val undecidedMessage = resources.getQuantityString(
                R.plurals.swiper_delete_confirmation_message_partial_undecided,
                state.undecidedCount,
                state.undecidedCount,
            )
            "$deleteMessage\n\n$undecidedMessage"
        } else {
            deleteMessage
        }

        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(R.string.swiper_delete_confirmation_title)
            setMessage(message)
            setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                vm.retryAllFailed()  // Always call - no-op if no failed items
                vm.finalize()
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action, null)
        }.show()
    }

}
