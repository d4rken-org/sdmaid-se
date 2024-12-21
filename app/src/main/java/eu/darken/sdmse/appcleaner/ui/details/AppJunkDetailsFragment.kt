package eu.darken.sdmse.appcleaner.ui.details

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcleanerDetailsFragmentBinding
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import javax.inject.Inject

@AndroidEntryPoint
class AppJunkDetailsFragment : Fragment3(R.layout.appcleaner_details_fragment) {

    override val vm: AppJunkDetailsViewModel by viewModels()
    override val ui: AppcleanerDetailsFragmentBinding by viewBinding()

    @Inject lateinit var dashAdapter: DashboardAdapter

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

        val pagerAdapter = AppJunkDetailsPagerAdapter(requireActivity(), childFragmentManager)
        ui.viewpager.apply {
            adapter = pagerAdapter
            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
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
                log { "state.target: ${state.target}" }
                state.items.indexOfFirst { it.identifier == state.target }
                    .takeIf { it != -1 }
                    ?.let { viewpager.currentItem = it }
            }
        }

        vm.events.observe2 { event ->
            when (event) {
                is AppJunkDetailsEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
