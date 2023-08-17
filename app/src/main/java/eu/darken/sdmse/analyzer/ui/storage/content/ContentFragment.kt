package eu.darken.sdmse.analyzer.ui.storage.content

import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AnalyzerContentFragmentBinding

@AndroidEntryPoint
class ContentFragment : Fragment3(R.layout.analyzer_content_fragment) {

    override val vm: ContentViewModel by viewModels()
    override val ui: AnalyzerContentFragmentBinding by viewBinding()

    private val onBackPressedcallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            vm.onNavigateBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedcallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setNavigationOnClickListener { vm.onNavigateBack() }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> false
                }
            }

        }

        val adapter = ContentAdapter()
        ui.list.setupDefaults(adapter)
        installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_analyzer_content_list_cab,
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<ContentAdapter.Item> ->
                when (item.itemId) {
                    R.id.action_exclude_selected -> {
                        vm.exclude(selected)
                        tracker.clearSelection()
                        true
                    }

                    R.id.action_delete_selected -> {
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                            setMessage(
                                getString(
                                    eu.darken.sdmse.common.R.string.general_delete_confirmation_message_selected_x_items,
                                    selected.size,
                                )
                            )
                            setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                                vm.delete(selected)
                                tracker.clearSelection()
                            }
                            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                        }.show()
                        true
                    }

                    R.id.action_create_filter_selected -> {
                        vm.createFilter(selected)
                        tracker.clearSelection()
                        true
                    }

                    else -> false
                }
            }
        )

        vm.state.observe2(ui) { state ->
            toolbar.title = state.title?.get(requireContext())
            toolbar.subtitle = state.subtitle?.get(requireContext())

            adapter.update(state.items)
            loadingOverlay.setProgress(state.progress)
            list.isInvisible = state.progress != null
        }

        vm.events.observe2 { event ->
            when (event) {
                is ContentItemEvents.ShowNoAccessHint -> Snackbar.make(
                    requireView(),
                    R.string.analyzer_content_access_opaque,
                    Snackbar.LENGTH_SHORT
                ).show()

                is ContentItemEvents.ExclusionsCreated -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(R.plurals.exclusion_x_new_exclusions, event.count),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction(eu.darken.sdmse.common.R.string.general_view_action) {
                        ContentFragmentDirections.goToExclusions().navigate()
                    }
                    .show()

                is ContentItemEvents.ContentDeleted -> Snackbar.make(
                    requireView(),
                    requireContext().getQuantityString2(
                        eu.darken.sdmse.common.R.plurals.general_delete_success_deleted_x_freed_y,
                        event.count,
                        event.count,
                        Formatter.formatShortFileSize(requireContext(), event.freedSpace)
                    ),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Fragment")
    }
}
