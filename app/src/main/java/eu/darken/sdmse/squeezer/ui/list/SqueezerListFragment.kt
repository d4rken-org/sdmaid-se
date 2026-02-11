package eu.darken.sdmse.squeezer.ui.list

import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.navigation.getSpanCount
import eu.darken.sdmse.common.previews.PreviewFragmentArgs
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.squeezer.ui.PreviewCompressionDialog
import eu.darken.sdmse.databinding.SqueezerListFragmentBinding
import java.lang.Integer.max
import javax.inject.Inject

@AndroidEntryPoint
class SqueezerListFragment : Fragment3(R.layout.squeezer_list_fragment) {

    override val vm: SqueezerListViewModel by viewModels()
    override val ui: SqueezerListFragmentBinding by viewBinding()

    @Inject lateinit var previewDialog: PreviewCompressionDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.list, bottom = true)
            insetsPadding(ui.loadingOverlay, bottom = true)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_toggle_layout_mode -> {
                        vm.toggleLayoutMode()
                        true
                    }

                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        ui.fabCompressAll.setOnClickListener { vm.compressAll() }

        fun determineSpanCount(mode: LayoutMode): Int = when (mode) {
            LayoutMode.LINEAR -> getSpanCount()
            LayoutMode.GRID -> max(getSpanCount(widthDp = 144), 3)
        }

        val adapter = SqueezerListAdapter()
        ui.list.setupDefaults(
            adapter = adapter,
            layouter = GridLayoutManager(
                context,
                determineSpanCount(vm.layoutMode),
                VERTICAL,
                false,
            ),
            verticalDividers = false,
        )

        val selectionTracker = installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_squeezer_list_cab,
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<SqueezerListAdapter.Item> ->
                when (item.itemId) {
                    R.id.action_compress_selected -> {
                        vm.compress(selected)
                        true
                    }

                    R.id.action_exclude_selected -> {
                        vm.exclude(selected)
                        tracker.clearSelection()
                        true
                    }

                    else -> false
                }
            },
            onChange = { tracker ->
                ui.fabCompressAll.isVisible = !tracker.hasSelection()
            },
            cabTitle = { selected ->
                val count = selected.size
                val totalSavings = selected.mapNotNull { it.image.estimatedSavings }.sum()
                if (totalSavings > 0) {
                    val savingsText = Formatter.formatShortFileSize(requireContext(), totalSavings)
                    getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, count) + " • ~$savingsText"
                } else {
                    getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, count)
                }
            },
        )

        vm.state.observe2(ui) { state ->
            list.isInvisible = state.progress != null
            loadingOverlay.setProgress(state.progress)

            (list.layoutManager as GridLayoutManager).spanCount = determineSpanCount(state.layoutMode)

            if (state.progress == null) adapter.update(state.items)

            fabCompressAll.isVisible = state.progress == null
                    && state.items.isNotEmpty()
                    && !selectionTracker.hasSelection()

            toolbar.apply {
                subtitle = if (state.progress == null) {
                    val count = state.items.size
                    val totalSavings = state.items.mapNotNull { it.image.estimatedSavings }.sum()
                    if (totalSavings > 0) {
                        val savingsText = Formatter.formatShortFileSize(requireContext(), totalSavings)
                        getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, count) + " • ~$savingsText"
                    } else {
                        getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, count)
                    }
                } else {
                    null
                }
                menu.findItem(R.id.action_toggle_layout_mode).apply {
                    isVisible = state.progress == null
                    setIcon(
                        when (state.layoutMode) {
                            LayoutMode.LINEAR -> R.drawable.baseline_grid_view_24
                            LayoutMode.GRID -> R.drawable.baseline_list_alt_24
                        }
                    )
                }
            }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is SqueezerListEvents.ConfirmCompression -> previewDialog.show(
                    items = event.items.map { it.image },
                    quality = event.quality,
                    onPositive = { quality ->
                        vm.compress(event.items, confirmed = true, qualityOverride = quality)
                        selectionTracker.clearSelection()
                    },
                    onNegative = {},
                )

                is SqueezerListEvents.ExclusionsCreated -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(R.plurals.exclusion_x_new_exclusions, event.count),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction(eu.darken.sdmse.common.R.string.general_view_action) {
                        SqueezerListFragmentDirections.goToExclusions().navigate()
                    }
                    .show()

                is SqueezerListEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()

                is SqueezerListEvents.PreviewEvent -> {
                    findNavController().navigate(
                        resId = R.id.goToPreview,
                        args = PreviewFragmentArgs(options = event.options).toBundle()
                    )
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
