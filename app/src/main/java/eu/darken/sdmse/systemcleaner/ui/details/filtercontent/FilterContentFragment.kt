package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SystemcleanerFiltercontentFragmentBinding
import eu.darken.sdmse.systemcleaner.core.filter.getLabel
import eu.darken.sdmse.systemcleaner.ui.details.FilterContentDetailsFragment
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements.FilterContentElementHeaderVH

@AndroidEntryPoint
class FilterContentFragment : Fragment3(R.layout.systemcleaner_filtercontent_fragment) {

    override val vm: FilterContentFragmentVM by viewModels()
    override val ui: SystemcleanerFiltercontentFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = FilterContentElementsAdapter()
        ui.list.apply {
            setupDefaults(adapter, dividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, _ ->
                cur !is FilterContentElementHeaderVH
            }
            addItemDecoration(divDec)
        }

        vm.info.observe2 {
            adapter.update(it.elements)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is FilterContentEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(
                        getString(
                            R.string.general_delete_confirmation_message_x,
                            event.identifier.getLabel(context)
                        )
                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ -> vm.doDelete(event.identifier) }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                }.show()
                is FilterContentEvents.ConfirmFileDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(
                        getString(
                            R.string.general_delete_confirmation_message_x,
                            event.path.userReadablePath.get(context)
                        )
                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ ->
                        vm.doDelete(event.identifier, event.path)
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                }.show()
                is FilterContentEvents.TaskForParent -> (parentFragment as FilterContentDetailsFragment).forwardTask(
                    event.task
                )
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
