package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SystemcleanerCustomfilterListFragmentBinding
import eu.darken.sdmse.systemcleaner.ui.customfilter.editor.CustomFilterEditorOptions
import javax.inject.Inject

@AndroidEntryPoint
class CustomFilterListFragment : Fragment3(R.layout.systemcleaner_customfilter_list_fragment) {

    override val vm: CustomFilterListViewModel by viewModels()
    override val ui: SystemcleanerCustomfilterListFragmentBinding by viewBinding()
    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_help -> {
                        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/SystemCleaner#custom-filter")
                        true
                    }

                    else -> false
                }
            }
        }

        val adapter = CustomFilterListAdapter()
        ui.list.setupDefaults(adapter)
        installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_systemcleaner_customfilter_list_cab,
            onPrepare = { tracker, _, menu ->
                menu.findItem(R.id.action_edit_selected)?.isVisible = tracker.selection.size() == 1
                true
            },
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<CustomFilterListAdapter.Item> ->
                when (item.itemId) {
                    R.id.action_remove_selected -> {
                        vm.remove(selected)
                        tracker.clearSelection()
                        true
                    }

                    R.id.action_edit_selected -> {
                        vm.edit(selected.first())
                        tracker.clearSelection()
                        true
                    }

                    else -> false
                }
            },
            onChange = {
                ui.mainAction.isVisible = !it.hasSelection()
            }
        )

        vm.state.observe2(ui) { state ->
            mainAction.apply {
                isGone = state.isPro == null
                setOnClickListener {
                    if (state.isPro == true) {
                        CustomFilterListFragmentDirections.actionCustomFilterListFragmentToCustomFilterEditorFragment(
                            initial = CustomFilterEditorOptions(),
                            identifier = null,
                        ).navigate()
                    } else if (state.isPro == false) {
                        CustomFilterListFragmentDirections.goToUpgradeFragment().navigate()
                    }
                }
            }

            adapter.update(state.items)
            loadingOverlay.isVisible = state.loading
            emptyOverlay.isVisible = state.items.isEmpty() && !state.loading
        }

        vm.events.observe2 { event ->
            when (event) {
                is CustomFilterListEvents.UndoRemove -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(
                            eu.darken.sdmse.common.R.plurals.general_remove_success_x_items,
                            event.exclusions.size
                        ),
                        Snackbar.LENGTH_INDEFINITE
                    )
                    .setAction(eu.darken.sdmse.common.R.string.general_undo_action) {
                        vm.restore(event.exclusions)
                    }
                    .show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
