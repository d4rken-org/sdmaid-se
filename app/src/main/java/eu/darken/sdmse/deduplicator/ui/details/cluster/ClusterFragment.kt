package eu.darken.sdmse.deduplicator.ui.details.cluster

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.selection.SelectionTracker
import androidx.viewpager.widget.ViewPager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DeduplicatorClusterFragmentBinding
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupFileVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupHeaderVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ClusterHeaderVH

@AndroidEntryPoint
class ClusterFragment : Fragment3(R.layout.deduplicator_cluster_fragment) {

    override val vm: ClusterViewModel by viewModels()
    override val ui: DeduplicatorClusterFragmentBinding by viewBinding()

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
        val adapter = ClusterAdapter()
        ui.list.apply {
            setupDefaults(adapter, dividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, _ ->
                cur !is ClusterAdapter.HeaderVH
            }
            addItemDecoration(divDec)
        }

        val selectionTracker = installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_deduplicator_cluster_cab,
            toolbar = requireParentFragment().requireView().findViewById(R.id.toolbar),
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<ClusterAdapter.Item> ->
                when (item.itemId) {
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
            if (state.progress == null) adapter.update(state.elements)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is ClusterEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(
                        when {
                            event.items.singleOrNull() is ClusterHeaderVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                (event.items.singleOrNull() as ClusterHeaderVH.Item)
                                    .cluster.label.get(requireContext()),
                            )

                            event.items.singleOrNull() is ChecksumGroupHeaderVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                (event.items.singleOrNull() as ChecksumGroupHeaderVH.Item)
                                    .group.label.get(requireContext()),
                            )

                            event.items.singleOrNull() is ChecksumGroupFileVH.Item -> getString(
                                eu.darken.sdmse.common.R.string.general_delete_confirmation_message_x,
                                (event.items.singleOrNull() as ChecksumGroupFileVH.Item)
                                    .duplicate.label.get(requireContext()),
                            )

                            else -> getString(
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
                }.show()

                is ClusterEvents.ViewItem -> {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(event.lookup.userReadablePath.get(requireContext()))
                        )
                        if (intent.resolveActivity(requireContext().packageManager) != null) {
                            startActivity(intent)
                        }
                    } catch (e: ActivityNotFoundException) {
                        e.asErrorDialogBuilder(requireActivity()).show()
                    }
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        parentPager.removeOnPageChangeListener(pageChangeListener)
        super.onDestroyView()
    }
}
