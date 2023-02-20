package eu.darken.sdmse.appcleaner.ui.list

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcleanerListFragmentBinding

@AndroidEntryPoint
class AppCleanerListFragment : Fragment3(R.layout.appcleaner_list_fragment) {

    override val vm: AppCleanerListFragmentVM by viewModels()
    override val ui: AppcleanerListFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        val adapter = AppCleanerListAdapter()
        ui.list.setupDefaults(adapter)

        vm.items.observe2(ui) {
            adapter.update(it)
            toolbar.subtitle = requireContext().getQuantityString2(R.plurals.result_x_items, it.size)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is AppCleanerListEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_clean_confirmation_title)
                    setMessage(
                        getString(
                            R.string.general_clean_confirmation_message_x,
                            event.appJunk.label.get(context)
                        )
                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ ->
                        vm.doDelete(event.appJunk)
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ ->
                        vm.showDetails(event.appJunk)
                    }
                }.show()
                is AppCleanerListEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
