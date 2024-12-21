package eu.darken.sdmse.stats.ui.pkgs

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.StatsAffectedPkgsFragmentBinding
import eu.darken.sdmse.stats.ui.pkgs.elements.AffectedPkgsHeaderVH

@AndroidEntryPoint
class AffectedPkgsFragment : Fragment3(R.layout.stats_affected_pkgs_fragment) {

    override val vm: AffectedPkgsViewModel by viewModels()
    override val ui: StatsAffectedPkgsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
        }

        ui.toolbar.setupWithNavController(findNavController())

        val adapter = AffectedPkgsAdapter()

        ui.list.apply {
            setupDefaults(adapter, verticalDividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, _ -> cur !is AffectedPkgsHeaderVH }
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
