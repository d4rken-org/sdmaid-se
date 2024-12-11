package eu.darken.sdmse.analyzer.ui.storage.app

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AnalyzerAppFragmentBinding

@AndroidEntryPoint
class AppDetailsFragment : Fragment3(R.layout.analyzer_app_fragment) {

    override val vm: AppDetailsViewModel by viewModels()
    override val ui: AnalyzerAppFragmentBinding by viewBinding()

    private val menuRefreshAction: MenuItem?
        get() = ui.toolbar.menu?.findItem(R.id.menu_action_refresh)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_refresh -> {
                        vm.refresh()
                        true
                    }

                    else -> false
                }
            }
        }
        val adapter = AppDetailsAdapter()
        ui.list.setupDefaults(adapter, verticalDividers = false)

        vm.state.observe2(ui) { state ->
            toolbar.title = state.pkgStat.label.get(requireContext())
            toolbar.subtitle = state.storage.label.get(requireContext())

            adapter.update(state.items)
            loadingOverlay.setProgress(state.progress)
            list.isInvisible = state.progress != null
            menuRefreshAction?.isVisible = state.progress == null
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Analyzer", "App", "Details", "Fragment")
    }
}
