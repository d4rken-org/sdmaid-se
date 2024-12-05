package eu.darken.sdmse.corpsefinder.ui.list

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.navigation.getSpanCount
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.CorpsefinderListFragmentBinding

@AndroidEntryPoint
class CorpseFinderListFragment : Fragment3(R.layout.corpsefinder_list_fragment) {

    override val vm: CorpseFinderListViewModel by viewModels()
    override val ui: CorpsefinderListFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        val adapter = CorpseFinderListAdapter()
        ui.list.setupDefaults(
            adapter,
            horizontalDividers = true,
            layouter = GridLayoutManager(context, getSpanCount(), GridLayoutManager.VERTICAL, false),
        )

        val selectionTracker = installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_corpsefinder_list_cab,
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<CorpseFinderListAdapter.Item> ->
                when (item.itemId) {
                    R.id.action_delete_selected -> {
                        vm.delete(selected)
                        true
                    }

                    R.id.action_exclude_selected -> {
                        vm.exclude(selected)
                        tracker.clearSelection()
                        true
                    }

                    else -> false
                }
            }
        )

        vm.state.observe2(ui) { state ->
            list.isInvisible = state.progress != null
            loadingOverlay.setProgress(state.progress)

            if (state.progress == null) adapter.update(state.items)

            toolbar.subtitle = if (state.progress == null) {
                getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, state.items.size)
            } else {
                null
            }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is CorpseFinderListEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(
                        if (event.items.size == 1) {
                            getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                (event.items.single() as CorpseFinderListRowVH.Item).corpse.lookup.userReadableName.get(
                                    context
                                )
                            )
                        } else {
                            getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_selected_x_items,
                                event.items.size
                            )
                        }

                    )
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                        vm.delete(event.items, confirmed = true)
                        selectionTracker.clearSelection()
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    if (event.items.size == 1) {
                        setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ ->
                            vm.showDetails(event.items.first())
                            selectionTracker.clearSelection()
                        }
                    }
                }.show()

                is CorpseFinderListEvents.ExclusionsCreated -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(R.plurals.exclusion_x_new_exclusions, event.count),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction(eu.darken.sdmse.common.R.string.general_view_action) {
                        CorpseFinderListFragmentDirections.goToExclusions().navigate()
                    }
                    .show()

                is CorpseFinderListEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
