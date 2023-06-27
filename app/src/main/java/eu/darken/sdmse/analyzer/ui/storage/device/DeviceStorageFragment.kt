package eu.darken.sdmse.analyzer.ui.storage.device

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AnalyzerDeviceFragmentBinding

@AndroidEntryPoint
class DeviceStorageFragment : Fragment3(R.layout.analyzer_device_fragment) {

    override val vm: DeviceStorageViewModel by viewModels()
    override val ui: AnalyzerDeviceFragmentBinding by viewBinding()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> false
                }
            }

        }

        val adapter = DeviceStorageAdapter()
        ui.list.setupDefaults(adapter, dividers = false)

        vm.state.observe2(ui) { state ->
            adapter.update(state.storages)
            loadingOverlay.setProgress(state.progress)
            list.isInvisible = state.progress != null
            refreshAction.isInvisible = state.progress != null
        }

        ui.refreshAction.setOnClickListener { vm.refresh() }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Fragment")
    }
}
