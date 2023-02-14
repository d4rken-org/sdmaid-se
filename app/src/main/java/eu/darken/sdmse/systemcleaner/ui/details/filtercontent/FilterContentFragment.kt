package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SystemcleanerFiltercontentFragmentBinding
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementHeaderVH

@AndroidEntryPoint
class FilterContentFragment : Fragment3(R.layout.systemcleaner_filtercontent_fragment) {

    override val vm: FilterContentFragmentVM by viewModels()
    override val ui: SystemcleanerFiltercontentFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = FilterContentElementsAdapter()
        ui.list.apply {
            setupDefaults(adapter, dividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, _ ->
                cur !is FilterContentElementHeaderVH
            }
            addItemDecoration(divDec)
        }

        vm.info.observe2 {
            adapter.update(it.elements)
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
