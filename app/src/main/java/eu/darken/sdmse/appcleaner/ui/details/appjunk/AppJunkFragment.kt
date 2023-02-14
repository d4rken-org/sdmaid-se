package eu.darken.sdmse.appcleaner.ui.details.appjunk

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcleanerAppjunkFragmentBinding

@AndroidEntryPoint
class AppJunkFragment : Fragment3(R.layout.appcleaner_appjunk_fragment) {

    override val vm: AppJunkFragmentVM by viewModels()
    override val ui: AppcleanerAppjunkFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = AppJunkElementsAdapter()
        ui.list.apply {
            setupDefaults(adapter, dividers = false)
            addItemDecoration(AppJunkElementDivider(requireContext()))
        }

        vm.info.observe2 {
            adapter.update(it.elements)
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
