package eu.darken.sdmse.appcleaner.ui.details.appjunk

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileCategoryVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementHeaderVH
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
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
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, next ->
                when {
                    cur is AppJunkElementHeaderVH -> false
                    cur is AppJunkElementFileCategoryVH -> false
                    cur is AppJunkElementFileVH && next !is AppJunkElementFileVH -> false
                    else -> true
                }
            }
            addItemDecoration(divDec)
        }

        vm.info.observe2 {
            adapter.update(it.elements)
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
