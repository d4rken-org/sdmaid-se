package eu.darken.sdmse.analyzer.ui.storage.apps

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getSpanCount
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AnalyzerAppsFragmentBinding

@AndroidEntryPoint
class AppsFragment : Fragment3(R.layout.analyzer_apps_fragment) {

    override val vm: AppsViewModel by viewModels()
    override val ui: AnalyzerAppsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> false
                }
            }

        }

        val adapter = AppsAdapter()
        ui.list.setupDefaults(
            adapter,
            horizontalDividers = true,
            layouter = GridLayoutManager(context, getSpanCount(), GridLayoutManager.VERTICAL, false),
        )

        vm.state.observe2(ui) { state ->
            toolbar.subtitle = state.storage.label.get(requireContext())

            adapter.update(state.apps)
            loadingOverlay.setProgress(state.progress)
            list.isInvisible = state.progress != null
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Apps", "Fragment")
    }
}
