package eu.darken.sdmse.deduplicator.ui.details.cluster

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.SelectionTracker.SelectionPredicate
import androidx.viewpager.widget.ViewPager
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.installListSelection
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.previews.PreviewFragmentArgs
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DeduplicatorClusterFragmentBinding
import eu.darken.sdmse.deduplicator.ui.PreviewDeletionDialog
import javax.inject.Inject

@AndroidEntryPoint
class ClusterFragment : Fragment3(R.layout.deduplicator_cluster_fragment) {

    override val vm: ClusterViewModel by viewModels()
    override val ui: DeduplicatorClusterFragmentBinding by viewBinding()

    @Inject lateinit var previewDialog: PreviewDeletionDialog

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

        val adapter = ClusterAdapter()
        ui.list.apply {
            setupDefaults(adapter, verticalDividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { prev, cur, next ->
                cur is ClusterAdapter.DuplicateItem.VH && next is ClusterAdapter.DuplicateItem.VH
            }
            addItemDecoration(divDec)
        }

        var selectableMax = 0

        selectionTracker = installListSelection(
            adapter = adapter,
            cabMenuRes = R.menu.menu_deduplicator_cluster_cab,
            toolbar = requireParentFragment().requireView().findViewById(R.id.toolbar),
            onSelected = { tracker: SelectionTracker<String>, item: MenuItem, selected: List<ClusterAdapter.Item> ->
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
            },
            selectionPredicate = object : SelectionPredicate<String>() {

                private val selectionCount: Int
                    get() = selectionTracker?.selection?.size() ?: selectableMax

                override fun canSetStateForKey(key: String, nextState: Boolean): Boolean = if (nextState) {
                    selectableMax > selectionCount
                } else {
                    true
                }

                override fun canSetStateAtPosition(position: Int, nextState: Boolean): Boolean = if (nextState) {
                    selectableMax > selectionCount
                } else {
                    true
                }

                override fun canSelectMultiple(): Boolean = true
            },
        )

        parentPager.addOnPageChangeListener(pageChangeListener)

        vm.state.observe2(ui) { state ->
            if (state.progress == null) adapter.update(state.elements)
            selectableMax = state.elements.filterIsInstance<ClusterAdapter.DuplicateItem>().count()
            if (!state.allowDeleteAll) selectableMax-- // Prevent last item selection
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is ClusterEvents.ConfirmDeletion -> previewDialog.show(
                    mode = when {
                        event.items.singleOrNull() is ClusterAdapter.ClusterItem -> PreviewDeletionDialog.Mode.Clusters(
                            clusters = listOf((event.items.single() as ClusterAdapter.ClusterItem).cluster),
                            event.allowDeleteAll,
                        )

                        event.items.singleOrNull() is ClusterAdapter.GroupItem -> PreviewDeletionDialog.Mode.Groups(
                            groups = listOf((event.items.single() as ClusterAdapter.GroupItem).group),
                            event.allowDeleteAll,
                        )

                        event.items.all { it is ClusterAdapter.DuplicateItem } -> PreviewDeletionDialog.Mode.Duplicates(
                            duplicates = event.items.map { (it as ClusterAdapter.DuplicateItem).duplicate },
                        )

                        else -> throw IllegalArgumentException("Unexpected adapter items: ${event.items}")
                    },
                    onPositive = { deleteAll ->
                        vm.delete(event.items, confirmed = true, deleteAll)
                        selectionTracker?.clearSelection()
                    },
                    onNeutral = event.items
                        .filterIsInstance<ClusterAdapter.DuplicateItem>()
                        .takeIf { it.size == 1 }
                        ?.let {
                            { vm.open(it.single()) }
                        }
                )

                is ClusterEvents.OpenDuplicate -> {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(event.lookup.userReadablePath.get(requireContext()))
                        )
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        e.asErrorDialogBuilder(requireActivity()).show()
                    }
                }

                is ClusterEvents.ViewDuplicate -> {
                    findNavController().navigate(
                        resId = R.id.goToPreview,
                        args = PreviewFragmentArgs(options = event.options).toBundle()
                    )
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
