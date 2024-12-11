package eu.darken.sdmse.systemcleaner.ui.details

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SystemcleanerDetailsFragmentBinding

@AndroidEntryPoint
class FilterContentDetailsFragment : Fragment3(R.layout.systemcleaner_details_fragment) {

    override val vm: FilterContentDetailsViewModel by viewModels()
    override val ui: SystemcleanerDetailsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }
        val pagerAdapter = FilterContentDetailsPagerAdapter(requireActivity(), childFragmentManager)
        ui.viewpager.apply {
            adapter = pagerAdapter
            addOnPageChangeListener(object : OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    vm.updatePage(pagerAdapter.data[position].identifier)
                }

                override fun onPageScrollStateChanged(state: Int) {}
            })
        }
        ui.tablayout.setupWithViewPager(ui.viewpager, true)

        vm.state.observe2(ui) { state ->
            loadingOverlay.setProgress(state.progress)
            tablayout.isInvisible = state.progress != null
            viewpager.isInvisible = state.progress != null

            if (state.progress == null) {
                pagerAdapter.apply {
                    setData(state.items)
                    notifyDataSetChanged()
                }
                state.items.indexOfFirst { it.identifier == state.target }
                    .takeIf { it != -1 }
                    ?.let { viewpager.currentItem = it }
            }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is FilterContentDetailsEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
