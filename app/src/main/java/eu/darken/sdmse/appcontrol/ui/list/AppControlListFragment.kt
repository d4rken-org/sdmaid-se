package eu.darken.sdmse.appcontrol.ui.list

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcontrolListFragmentBinding

@AndroidEntryPoint
class AppControlListFragment : Fragment3(R.layout.appcontrol_list_fragment) {

    override val vm: AppControlListFragmentVM by viewModels()
    override val ui: AppcontrolListFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        val adapter = AppControlListAdapter()
        ui.list.setupDefaults(adapter)

        vm.items.observe2(ui) { state ->
            updateProgress(state.progress)

            list.isVisible = state.appInfos != null
            if (state.appInfos != null) {
                toolbar.subtitle = requireContext().getQuantityString2(R.plurals.result_x_items, state.appInfos.size)
                adapter.update(state.appInfos)
            } else {
                toolbar.subtitle = null
            }
        }

        vm.events.observe2(ui) {
            when (it) {

            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    private fun AppcontrolListFragmentBinding.updateProgress(data: Progress.Data?) {
        loadingOverlay.isVisible = data != null
        if (data == null) return


        loadingPrimary.apply {
            val newText = data.primary.get(context)
            text = newText
            isInvisible = newText.isEmpty()
        }
        loadingSecondary.apply {
            val newText = data.secondary.get(context)
            text = newText
            isInvisible = newText.isEmpty()
        }

        loadingIndicator.apply {
            isGone = data.count is Progress.Count.None
            when (data.count) {
                is Progress.Count.Counter -> {
                    isIndeterminate = data.count.current == 0L
                    progress = data.count.current.toInt()
                    max = data.count.max.toInt()
                }
                is Progress.Count.Percent -> {
                    isIndeterminate = data.count.current == 0L
                    progress = data.count.current.toInt()
                    max = data.count.max.toInt()
                }
                is Progress.Count.Indeterminate -> {
                    isIndeterminate = true
                }
                is Progress.Count.Size -> {}
                is Progress.Count.None -> {}
            }
        }
        loadingIndicatorText.apply {
            text = data.count.displayValue(context)
            isInvisible =
                data.count is Progress.Count.Indeterminate || data.count is Progress.Count.None || data.count.current == 0L
        }
    }
}
