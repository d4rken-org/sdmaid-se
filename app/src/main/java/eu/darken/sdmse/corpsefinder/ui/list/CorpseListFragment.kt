package eu.darken.sdmse.corpsefinder.ui.list

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
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
import eu.darken.sdmse.databinding.CorpsefinderListFragmentBinding

@AndroidEntryPoint
class CorpseListFragment : Fragment3(R.layout.corpsefinder_list_fragment) {

    override val vm: CorpseListFragmentVM by viewModels()
    override val ui: CorpsefinderListFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        val adapter = CorpseListAdapter()
        ui.list.setupDefaults(adapter)

        vm.state.observe2(ui) { state ->
            adapter.update(state.items)

            list.isInvisible = state.progress != null
            loadingOverlay.setProgress(state.progress)

            toolbar.subtitle = requireContext().getQuantityString2(R.plurals.result_x_items, state.items.size)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is CorpseListEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(
                        getString(
                            R.string.general_delete_confirmation_message_x,
                            event.corpse.path.userReadableName.get(context)
                        )
                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ ->
                        vm.doDelete(event.corpse)
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ ->
                        vm.showDetails(event.corpse)
                    }
                }.show()
                is CorpseListEvents.TaskResult -> Snackbar.make(
                    requireView(),
                    event.result.primaryInfo.get(requireContext()),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
