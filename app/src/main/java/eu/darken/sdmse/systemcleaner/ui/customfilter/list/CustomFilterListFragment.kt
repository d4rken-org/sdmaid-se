package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
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

    private lateinit var importPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportPickerLauncher: ActivityResultLauncher<Intent>

    private var currentSnackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

            vm.importFilter(uriList)
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
            setOnMenuItemClickListener { menuItem ->
                currentSnackbar?.let {
                    it.dismiss()
                    currentSnackbar = null
                }
                when (menuItem.itemId) {
                    R.id.menu_action_help -> {
                        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/SystemCleaner#custom-filter")
                        true
                    }

                    R.id.menu_action_import -> {
                        vm.importFilter()
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
                currentSnackbar?.let {
                    it.dismiss()
                    currentSnackbar = null
                }
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

                    R.id.menu_action_export -> {
                        vm.exportFilter(selected)
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
                    .also { currentSnackbar = it }
                    .show()

                is CustomFilterListEvents.ImportEvent -> {
                    importPickerLauncher.launch(event.intent)
                }

                is CustomFilterListEvents.ExportEvent -> {
                    exportPickerLauncher.launch(event.intent)
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "List")
    }
}
