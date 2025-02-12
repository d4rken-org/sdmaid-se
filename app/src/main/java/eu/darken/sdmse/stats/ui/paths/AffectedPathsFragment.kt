package eu.darken.sdmse.stats.ui.paths

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.StatsAffectedPathsFragmentBinding
import eu.darken.sdmse.stats.ui.paths.elements.AffectedPathsHeaderVH

@AndroidEntryPoint
class AffectedPathsFragment : Fragment3(R.layout.stats_affected_paths_fragment) {

    override val vm: AffectedPathsViewModel by viewModels()
    override val ui: StatsAffectedPathsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.list, bottom = true)
            insetsPadding(ui.loadingOverlay, bottom = true)
        }

        ui.toolbar.setupWithNavController(findNavController())

        val adapter = AffectedPathsAdapter()

        ui.list.apply {
            setupDefaults(adapter, verticalDividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, _ -> cur !is AffectedPathsHeaderVH }
            addItemDecoration(divDec)
        }

        vm.state.observe2(ui) { state ->
            state.elements?.let { adapter.update(it) }
            list.isVisible = state.elements != null
            loadingOverlay.isVisible = state.elements == null
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
