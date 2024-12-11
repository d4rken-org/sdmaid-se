package eu.darken.sdmse.analyzer.ui.storage.storage

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
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
import eu.darken.sdmse.databinding.AnalyzerStorageFragmentBinding

@AndroidEntryPoint
class StorageContentFragment : Fragment3(R.layout.analyzer_storage_fragment) {

    override val vm: StorageContentViewModel by viewModels()
    override val ui: AnalyzerStorageFragmentBinding by viewBinding()

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
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
            bottomHalf(ui.refreshActionContainer)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> false
                }
            }
            setNavigationOnClickListener { vm.onNavigateBack() }
        }

        val adapter = StorageContentAdapter()
        ui.list.setupDefaults(
            adapter,
            verticalDividers = false,
            layouter = GridLayoutManager(context, getSpanCount(), GridLayoutManager.VERTICAL, false)
        )

        vm.state.observe2(ui) { state ->
            toolbar.subtitle = state.storage.label.get(requireContext())

            adapter.update(state.content)
            loadingOverlay.setProgress(state.progress)
            list.isInvisible = state.progress != null
            refreshAction.isInvisible = state.progress != null
        }

        ui.refreshAction.setOnClickListener { vm.refresh() }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Content", "Fragment")
    }
}
