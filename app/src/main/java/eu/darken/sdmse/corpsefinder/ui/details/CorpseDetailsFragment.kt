package eu.darken.sdmse.corpsefinder.ui.details

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.CorpsefinderDetailsFragmentBinding

@AndroidEntryPoint
class CorpseDetailsFragment : Fragment3(R.layout.corpsefinder_details_fragment) {

    override val vm: CorpseDetailsFragmentVM by viewModels()
    override val ui: CorpsefinderDetailsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }
        val adapter = CorpseDetailsPagerAdapter(requireActivity(), childFragmentManager)
        ui.viewpager.adapter = adapter
        ui.tablayout.setupWithViewPager(ui.viewpager, true)

        vm.items.observe2 {
            adapter.setData(it)
            adapter.notifyDataSetChanged()
        }


        super.onViewCreated(view, savedInstanceState)
    }
}
