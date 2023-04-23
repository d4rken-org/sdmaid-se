package eu.darken.sdmse.appcleaner.ui.details.appjunk

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.ui.details.AppJunkDetailsFragment
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileCategoryVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementHeaderVH
import eu.darken.sdmse.appcleaner.ui.labelRes
import eu.darken.sdmse.common.isNotNullOrEmpty
import eu.darken.sdmse.common.lists.ViewHolderBasedDivider
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.AppcleanerAppjunkFragmentBinding

@AndroidEntryPoint
class AppJunkFragment : Fragment3(R.layout.appcleaner_appjunk_fragment) {

    override val vm: AppJunkFragmentVM by viewModels()
    override val ui: AppcleanerAppjunkFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = AppJunkElementsAdapter()
        ui.list.apply {
            setupDefaults(adapter, dividers = false)
            val divDec = ViewHolderBasedDivider(requireContext()) { _, cur, next ->
                when {
                    cur is AppJunkElementHeaderVH -> false
                    cur is AppJunkElementFileCategoryVH -> false
                    cur is AppJunkElementFileVH && next !is AppJunkElementFileVH -> false
                    else -> true
                }
            }
            addItemDecoration(divDec)
        }

        vm.state.observe2(ui) { state ->
            adapter.update(state.items)

            list.isInvisible = state.progress != null
            loadingOverlay.setProgress(state.progress)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is AppJunkEvents.ConfirmDeletion -> MaterialAlertDialogBuilder(requireContext()).apply {
                    val task = event.deletionTask
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(
                        when {
                            task.onlyInaccessible -> getString(
                                R.string.general_delete_confirmation_message_x_for_x,
                                getString(R.string.appcleaner_item_caches_inaccessible_title),
                                event.appJunk.label.get(context),
                            )
                            task.targetContents.isNotNullOrEmpty() -> getString(
                                R.string.general_delete_confirmation_message_x,
                                task.targetContents!!.first().userReadablePath.get(context),
                            )
                            task.targetFilters.isNotNullOrEmpty() -> getString(
                                R.string.general_delete_confirmation_message_x_for_x,
                                getString(task.targetFilters!!.first().labelRes),
                                event.appJunk.label.get(context),
                            )
                            else -> getString(
                                R.string.general_delete_confirmation_message_x,
                                event.appJunk.label.get(context)
                            )
                        }

                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ ->
                        vm.doDelete(task)
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    if (task.targetContents.isNotNullOrEmpty()) {
                        setNeutralButton(R.string.general_exclude_action) { _, _ ->
                            vm.doExclude(task.targetPkgs!!.first(), task.targetContents!!.first())
                        }
                    }
                }.show()

                is AppJunkEvents.TaskForParent -> (parentFragment as AppJunkDetailsFragment).forwardTask(event.task)
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
