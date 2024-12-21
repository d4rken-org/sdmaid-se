package eu.darken.sdmse.stats.ui.reports

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.navigation.getSpanCount
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.StatsReportsFragmentBinding

@AndroidEntryPoint
class ReportsFragment : Fragment3(R.layout.stats_reports_fragment) {

    override val vm: ReportsViewModel by viewModels()
    override val ui: StatsReportsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.list)
        }

        ui.toolbar.setupWithNavController(findNavController())

        val adapter = ReportsAdapter()
        ui.list.setupDefaults(
            adapter = adapter,
            layouter = GridLayoutManager(context, getSpanCount(), GridLayoutManager.VERTICAL, false)
        )

        vm.items.observe2(ui) { state ->
            adapter.update(state.listItems)
            loadingOverlay.isGone = state.listItems != null
            list.isGone = state.listItems == null
            state.listItems?.let {
                toolbar.subtitle = getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, it.size)
            }
        }

        vm.event.observe2 { event ->
            when (event) {
                is ReportsEvent.ShowError -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_error_label)
                    setMessage(event.msg)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_dismiss_action) { _, _ -> }
                }.show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
