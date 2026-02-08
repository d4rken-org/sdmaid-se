package eu.darken.sdmse.deduplicator.ui.details

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
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.ui.updateLiftStatus
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.uix.setDataIfChanged
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DeduplicatorDetailsFragmentBinding

@AndroidEntryPoint
class DeduplicatorDetailsFragment : Fragment3(R.layout.deduplicator_details_fragment) {

    override val vm: DeduplicatorDetailsViewModel by viewModels()
    override val ui: DeduplicatorDetailsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.appbarlayout, top = true, left = true, right = true)
            insetsPadding(ui.loadingOverlay, bottom = true)
        }

        ui.toolbar.apply {
            setupWithNavController(findNavController())
            inflateMenu(R.menu.menu_deduplicator_details)
            @Suppress("DEPRECATION")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_toggle_view_mode -> {
                        vm.toggleDirectoryView()
                        true
                    }

                    else -> super.onOptionsItemSelected(it)
                }
            }
        }
        val pagerAdapter = DeduplicatorDetailsPagerAdapter(requireActivity(), childFragmentManager)
        ui.viewpager.apply {
            adapter = pagerAdapter
            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    vm.updatePage(pagerAdapter.data[position].identifier)
                    pagerAdapter.getFragment(ui.viewpager.currentItem)?.view?.findViewById<View>(R.id.list)?.let {
                        ui.appbarlayout.updateLiftStatus(it)
                    }
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
                if (pagerAdapter.setDataIfChanged(state.items) { it.identifier }) {
                    state.items.indexOfFirst { it.identifier == state.target }
                        .takeIf { it != -1 }
                        ?.let { viewpager.currentItem = it }
                }
            }

            toolbar.menu.findItem(R.id.action_toggle_view_mode)?.apply {
                isVisible = state.progress == null
                setIcon(
                    if (state.isDirectoryViewEnabled) R.drawable.ic_baseline_format_list_bulleted_24
                    else R.drawable.ic_folder
                )
                setTitle(
                    if (state.isDirectoryViewEnabled) R.string.deduplicator_view_mode_groups_label
                    else R.string.deduplicator_view_mode_directories_label
                )
            }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is DeduplicatorDetailsEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
