package eu.darken.sdmse.exclusion.ui.list

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.ExclusionListFragmentBinding
import eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionEditorOptions
import javax.inject.Inject

@AndroidEntryPoint
class ExclusionListFragment : Fragment3(R.layout.exclusion_list_fragment) {

    override val vm: ExclusionListViewModel by viewModels()
    override val ui: ExclusionListFragmentBinding by viewBinding()
    @Inject lateinit var webpageTool: WebpageTool

    private lateinit var importPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportPickerLauncher: ActivityResultLauncher<Intent>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
            bottomHalf(ui.mainActionContainer)
        }

        importPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                log(TAG, WARN) { "importPickerLauncher returned ${result.resultCode}: ${result.data}" }
                return@registerForActivityResult
            }

            val uriList = mutableListOf<Uri>()

            val clipData = result.data?.clipData
            if (clipData != null) {
                (0 until clipData.itemCount).forEach {
                    uriList.add(clipData.getItemAt(it).uri)
                }
            } else {
                result.data?.data?.let { uriList.add(it) }
            }

            vm.importExclusions(uriList)
        }

        exportPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                log(TAG, WARN) { "exportPickerLauncher returned ${result.resultCode}: ${result.data}" }
                return@registerForActivityResult
            }

            result.data?.data?.let { uri ->
                vm.performExport(uri)
            }
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_info -> {
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setMessage(R.string.exclusion_explanation_body1)
                            setNeutralButton(eu.darken.sdmse.common.R.string.general_more_info_action) { _, _ ->
                                webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Exclusions")
                            }
                        }.show()
                        true
                    }

                    R.id.menu_restore_default_exclusions -> {
                        vm.resetDefaultExclusions()
                        true
                    }

                    R.id.menu_action_import -> {
                        vm.importExclusions()
                        true
                    }

                    R.id.menu_show_defaults -> {
                        it.isChecked = !it.isChecked
                        vm.showDefeaultExclusions(it.isChecked)
                        true
                    }

                    else -> false
                }
            }
        }

        ui.mainAction.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                val actions = arrayOf(
                    getString(R.string.exclusion_type_package),
                    getString(R.string.exclusion_type_path),
                    getString(R.string.exclusion_type_segment),
                )
                setItems(actions) { dialog, which ->
                    when (which) {
                        0 -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.exclusion_create_pkg_hint,
                                Toast.LENGTH_LONG
                            ).show()
                            ExclusionListFragmentDirections.goToAppControlListFragment().navigate()
                        }

                        1 -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.exclusion_create_path_hint,
                                Toast.LENGTH_LONG
                            ).show()
                            ExclusionListFragmentDirections.goToDeviceStorageFragment().navigate()
                        }

                        2 -> ExclusionListFragmentDirections.actionExclusionsListFragmentToSegmentExclusionFragment(
                            exclusionId = null,
                            initial = SegmentExclusionEditorOptions()
                        ).navigate()
                    }
                }
                setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                }
            }.show()
        }

        val adapter = ExclusionListAdapter()
        ui.list.setupDefaults(adapter)
        installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_exclusions_list_cab,
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<ExclusionListAdapter.Item> ->
                when (item.itemId) {
                    R.id.action_remove_selected -> {
                        vm.remove(selected)
                        tracker.clearSelection()
                        true
                    }

                    R.id.menu_action_export -> {
                        vm.exportExclusions(selected)
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
            adapter.update(state.items)
            loadingOverlay.isVisible = state.loading
            emptyOverlay.isVisible = state.items.isEmpty() && !state.loading

            toolbar.menu.findItem(R.id.menu_show_defaults)?.apply {
                isVisible = true
                isChecked = state.showDefaults
            }
        }

        vm.events.observe2 { event ->
            when (event) {
                is ExclusionListEvents.UndoRemove -> Snackbar
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

                is ExclusionListEvents.ImportEvent -> {
                    importPickerLauncher.launch(event.intent)
                }

                is ExclusionListEvents.ExportEvent -> {
                    exportPickerLauncher.launch(event.intent)
                }

                is ExclusionListEvents.ImportSuccess -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(
                            R.plurals.exclusion_x_new_exclusions,
                            event.exclusions.size
                        ),
                        Snackbar.LENGTH_INDEFINITE
                    )
                    .show()

                is ExclusionListEvents.ExportSuccess -> Snackbar
                    .make(
                        requireView(),
                        getString(eu.darken.sdmse.common.R.string.general_result_success_message),
                        Snackbar.LENGTH_INDEFINITE
                    )
                    .show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Exclusions", "List")
    }
}
