package eu.darken.sdmse.common.debug.logviewer.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DebugLogviewFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class LogViewFragment : Fragment3(R.layout.debug_logview_fragment) {

    override val vm: LogViewViewModel by viewModels()
    override val ui: DebugLogviewFragmentBinding by viewBinding()
    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
        }

        val adapter = LogViewerAdapter()
        ui.list.setupDefaults(adapter)

        vm.log.observe2(ui) {
            adapter.update(it)
            adapter.notifyDataSetChanged()
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
