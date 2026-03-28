package eu.darken.sdmse.analyzer.ui.storage.content

import android.content.ActivityNotFoundException
import eu.darken.sdmse.analyzer.core.storage.SystemStorageScanner
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.resolveSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.navigation.getSpanCount
import eu.darken.sdmse.common.navigation.safeNavigate
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.uix.Fragment3
import kotlin.math.max
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.analyzer.databinding.AnalyzerContentFragmentBinding
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.common.R as CommonR

@AndroidEntryPoint
class ContentFragment : Fragment3(R.layout.analyzer_content_fragment) {

    override val vm: ContentViewModel by viewModels()
    override val ui: AnalyzerContentFragmentBinding by viewBinding()
    private var isReadOnly = false

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
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.list, bottom = true)
            insetsPadding(ui.loadingOverlay, top = true, bottom = true)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setNavigationOnClickListener { vm.onNavigateBack() }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_toggle_layout_mode -> {
                        vm.toggleLayoutMode()
                        true
                    }

                    else -> false
                }
            }
        }

        val adapter = ContentAdapter()
        val layoutManager = GridLayoutManager(context, getSpanCount(), GridLayoutManager.VERTICAL, false)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = when (adapter.data.getOrNull(position)) {
                is ContentInfoVH.Item -> layoutManager.spanCount
                else -> 1
            }
        }
        ui.list.setupDefaults(
            adapter,
            horizontalDividers = false,
            layouter = layoutManager,
        )

        installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_analyzer_content_list_cab,
            onPrepare = { tracker, mode, menu ->
                val selectedItems = resolveSelection(tracker, adapter.data, "onPrepare")
                val hasInaccessible = selectedItems.any {
                    when (it) {
                        is ContentItemListVH.Item -> it.content.inaccessible
                        is ContentItemGridVH.Item -> it.content.inaccessible
                        else -> true
                    }
                }
                menu.findItem(R.id.action_delete_selected)?.isVisible = !hasInaccessible && !isReadOnly
                menu.findItem(R.id.action_create_filter_selected)?.isVisible = !hasInaccessible && !isReadOnly
                menu.findItem(R.id.action_create_swiper_session)?.isVisible = !hasInaccessible && !isReadOnly
                true
            },
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
                                resources.getQuantityString(
                                    eu.darken.sdmse.common.R.plurals.general_delete_confirmation_message_selected_x_items,
                                    selected.size,
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

                    R.id.action_create_swiper_session -> {
                        vm.createSwiperSession(selected)
                        tracker.clearSelection()
                        true
                    }

                    else -> false
                }
            }
        )

        vm.state.observe2(ui) { state ->
            isReadOnly = state.isReadOnly
            toolbar.title = state.title?.get(requireContext())
            toolbar.subtitle = state.subtitle?.get(requireContext())

            val newSpanCount = when (state.layoutMode) {
                LayoutMode.LINEAR -> getSpanCount()
                LayoutMode.GRID -> max(getSpanCount(widthDp = 144), 3)
            }
            if (layoutManager.spanCount != newSpanCount) {
                layoutManager.spanCount = newSpanCount
            }

            toolbar.menu.findItem(R.id.action_toggle_layout_mode)?.setIcon(
                when (state.layoutMode) {
                    LayoutMode.LINEAR -> UiR.drawable.baseline_grid_view_24
                    LayoutMode.GRID -> CommonR.drawable.ic_baseline_view_list_24
                }
            )

            loadingOverlay.setProgress(state.progress)
            if (state.progress == null) adapter.update(state.items)
            list.isInvisible = state.progress != null
        }

        vm.events.observe2 { event ->
            when (event) {
                is ContentItemEvents.ShowNoAccessHint -> {
                    val isOtherItem = event.item.path == SystemStorageScanner.OTHER_PATH
                    if (isOtherItem) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(eu.darken.sdmse.analyzer.R.string.analyzer_storage_content_type_system_other_label)
                            .setMessage(eu.darken.sdmse.analyzer.R.string.analyzer_storage_content_type_system_other_desc)
                            .setPositiveButton(eu.darken.sdmse.common.R.string.general_dismiss_action, null)
                            .show()
                    } else {
                        Snackbar.make(
                            requireView(),
                            eu.darken.sdmse.analyzer.R.string.analyzer_content_access_opaque,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }

                is ContentItemEvents.ExclusionsCreated -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(eu.darken.sdmse.common.R.plurals.exclusion_x_new_exclusions, event.items.size),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction(eu.darken.sdmse.common.R.string.general_view_action) {
                        if (event.items.size == 1) {
                            vm.openExclusion(event.items.single())
                        } else {
                            safeNavigate(ExclusionsListRoute)
                        }
                    }
                    .show()

                is ContentItemEvents.ContentDeleted -> Snackbar.make(
                    requireView(),
                    run {
                        val itemText = getQuantityString2(
                            eu.darken.sdmse.common.R.plurals.general_delete_success_deleted_x,
                            event.count,
                        )
                        val spaceText = run {
                            val (spaceFormatted, spaceQuantity) = ByteFormatter.formatSize(
                                requireContext(),
                                event.freedSpace
                            )
                            getQuantityString2(
                                eu.darken.sdmse.common.R.plurals.general_result_x_space_freed,
                                spaceQuantity,
                                spaceFormatted,
                            )
                        }
                        "$itemText $spaceText"
                    },
                    Snackbar.LENGTH_LONG
                ).show()

                is ContentItemEvents.OpenContent -> {
                    try {
                        startActivity(event.intent)
                    } catch (e: ActivityNotFoundException) {
                        e.asErrorDialogBuilder(requireActivity()).show()
                    }
                }

                is ContentItemEvents.SwiperSessionCreated -> Snackbar
                    .make(
                        requireView(),
                        getQuantityString2(eu.darken.sdmse.analyzer.R.plurals.analyzer_content_swiper_session_created_x_items, event.itemCount),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction(eu.darken.sdmse.common.R.string.general_view_action) {
                        safeNavigate(SwiperSessionsRoute)
                    }
                    .show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Fragment")
    }
}
