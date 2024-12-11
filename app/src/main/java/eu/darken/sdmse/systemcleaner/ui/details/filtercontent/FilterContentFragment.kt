package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.selection.SelectionTracker
import androidx.viewpager.widget.ViewPager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SystemcleanerFiltercontentFragmentBinding
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementFileVH
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementHeaderVH

@AndroidEntryPoint
class FilterContentFragment : Fragment3(R.layout.systemcleaner_filtercontent_fragment) {

    override val vm: FilterContentViewModel by viewModels()
    override val ui: SystemcleanerFiltercontentFragmentBinding by viewBinding()

    private var selectionTracker: SelectionTracker<String>? = null

    private val pageChangeListener: ViewPager.OnPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

        override fun onPageSelected(position: Int) {
            selectionTracker?.clearSelection()
        }

        override fun onPageScrollStateChanged(state: Int) {}

    }

    private val parentPager: ViewPager
        get() = requireParentFragment().requireView().findViewById(R.id.viewpager)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            bottomHalf(ui.list)
        }

        val adapter = FilterContentElementsAdapter()
        ui.list.apply {
            setupDefaults(adapter, verticalDividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, _ ->
                cur !is FilterContentElementHeaderVH
            }
            addItemDecoration(divDec)
        }

        selectionTracker = installListSelection(
            adapter = adapter,
            toolbar = requireParentFragment().requireView().findViewById(R.id.toolbar),
            cabMenuRes = R.menu.menu_systemcleaner_filtercontent_cab,
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<FilterContentElementsAdapter.Item> ->
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

        parentPager.addOnPageChangeListener(pageChangeListener)

        vm.state.observe2(ui) { state ->
            if (state.progress == null) adapter.update(state.items)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is FilterContentEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(
                        when {
                            event.items.singleOrNull() is FilterContentElementHeaderVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                (event.items.single() as FilterContentElementHeaderVH.Item)
                                    .filterContent.label.get(context)
                            )

                            event.items.singleOrNull() is FilterContentElementFileVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                (event.items.single() as FilterContentElementFileVH.Item)
                                    .match.lookup.userReadablePath.get(context)
                            )

                            else -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_selected_x_items,
                                event.items.size
                            )
                        }
                    )
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                        vm.delete(event.items, confirmed = true)
                        selectionTracker?.clearSelection()
                    }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                }.show()
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        parentPager.removeOnPageChangeListener(pageChangeListener)
        super.onDestroyView()
    }
}
