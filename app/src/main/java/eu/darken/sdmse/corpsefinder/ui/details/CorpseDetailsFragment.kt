package eu.darken.sdmse.corpsefinder.ui.details

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
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

        vm.state.observe2(ui) { state ->
            adapter.setData(state.items)
            adapter.notifyDataSetChanged()
            state.items.indexOfFirst { it.path == state.target }
                .takeIf { it != -1 }
                ?.let { viewpager.currentItem = it }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is CorpseDetailsEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    fun forwardTask(task: CorpseFinderTask) {
        vm.forwardTask(task)
    }
}
