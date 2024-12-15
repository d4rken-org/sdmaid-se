package eu.darken.sdmse.appcleaner.ui.details.appjunk

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.selection.SelectionTracker
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileCategoryVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementHeaderVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementInaccessibleVH
import eu.darken.sdmse.appcleaner.ui.labelRes
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcleanerAppjunkFragmentBinding

@AndroidEntryPoint
class AppJunkFragment : Fragment3(R.layout.appcleaner_appjunk_fragment) {

    override val vm: AppJunkViewModel by viewModels()
    override val ui: AppcleanerAppjunkFragmentBinding by viewBinding()

    private var selectionTracker: SelectionTracker<String>? = null

    private val pageChangeListener: OnPageChangeListener = object : OnPageChangeListener {
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

        val adapter = AppJunkElementsAdapter()
        ui.list.apply {
            setupDefaults(adapter, verticalDividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, next ->
                when {
                    cur is AppJunkElementHeaderVH -> false
                    cur is AppJunkElementFileCategoryVH -> false
                    cur is AppJunkElementFileVH && next !is AppJunkElementFileVH -> false
                    else -> true
                }
            }
            addItemDecoration(divDec)
        }

        selectionTracker = installListSelection(
            adapter = adapter,
            toolbar = requireParentFragment().requireView().findViewById(R.id.toolbar),
            cabMenuRes = R.menu.menu_appcleaner_appjunk_cab,
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<AppJunkElementsAdapter.Item> ->
                when (item.itemId) {
                    R.id.action_exclude_selected -> {
                        vm.exclude(selected)
                        tracker.clearSelection()
                        true
                    }

                    R.id.action_delete_selected -> {
                        vm.delete(selected)
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
                is AppJunkEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(
                        when {
                            event.items.singleOrNull() is AppJunkElementInaccessibleVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                getString(R.string.appcleaner_item_caches_inaccessible_title),
                            )

                            event.items.singleOrNull() is AppJunkElementFileCategoryVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                getString((event.items.singleOrNull() as AppJunkElementFileCategoryVH.Item).category.labelRes),
                            )

                            event.items.singleOrNull() is AppJunkElementFileVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                (event.items.singleOrNull() as AppJunkElementFileVH.Item).match.path.userReadableName
                                    .get(requireContext()),
                            )

                            event.items.singleOrNull() is AppJunkElementHeaderVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                (event.items.singleOrNull() as AppJunkElementHeaderVH.Item).appJunk.label
                                    .get(requireContext())
                            )

                            else -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_selected_x_items,
                                event.items.size,
                            )
                        }

                    )
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                        vm.delete(event.items, confirmed = true)
                        selectionTracker!!.clearSelection()
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
