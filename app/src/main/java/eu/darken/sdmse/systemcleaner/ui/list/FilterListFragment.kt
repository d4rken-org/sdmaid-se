package eu.darken.sdmse.systemcleaner.ui.list

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SystemcleanerListFragmentBinding
import eu.darken.sdmse.systemcleaner.core.filter.getLabel

@AndroidEntryPoint
class FilterListFragment : Fragment3(R.layout.systemcleaner_list_fragment) {

    override val vm: FilterListFragmentVM by viewModels()
    override val ui: SystemcleanerListFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        val adapter = FilterListAdapter()
        ui.list.setupDefaults(adapter)

        vm.items.observe2(ui) {
            adapter.update(it)
            toolbar.subtitle = requireContext().getQuantityString2(R.plurals.result_x_items, it.size)
        }

        vm.events.observe2(ui) {
            when (it) {
                is FilterListEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(
                        getString(
                            R.string.general_delete_confirmation_message_x,
                            it.filterContent.filterIdentifier.getLabel(context)
                        )
                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ ->
                        vm.doDelete(it.filterContent)
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ ->
                        vm.showDetails(it.filterContent)
                    }
                }.show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
