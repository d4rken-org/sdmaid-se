package eu.darken.sdmse.main.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DashboardFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment3(R.layout.dashboard_fragment) {

    override val vm: DashboardViewModel by viewModels()
    override val ui: DashboardFragmentBinding by viewBinding()

    @Inject lateinit var dashAdapter: DashboardAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.list.setupDefaults(dashAdapter, dividers = false, fastscroll = false)

        vm.listItems.observe2(ui) {
            listProgress.isVisible = it.isEmpty()
            list.isVisible = it.isNotEmpty()
            dashAdapter.update(it)
        }

        ui.bottomBar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_upgrade -> {
                        DashboardFragmentDirections.goToUpgradeFragment().navigate()
                        true
                    }

                    R.id.menu_action_settings -> {
                        DashboardFragmentDirections.actionDashboardFragmentToSettingsContainerFragment().navigate()
                        true
                    }

                    else -> false
                }
            }
        }
        vm.bottomBarState.observe2(ui) { state ->
            if (state.activeTasks > 0 || state.queuedTasks > 0) {
                bottomBarText.apply {
                    text = getQuantityString2(R.plurals.tasks_activity_active_notification_message, state.activeTasks)
                    append("\n")
                    append(getQuantityString2(R.plurals.tasks_activity_queued_notification_message, state.queuedTasks))
                }
            } else if (state.totalItems > 0 || state.totalSize > 0L) {
                bottomBarText.apply {
                    text = requireContext().getString(
                        eu.darken.sdmse.common.R.string.x_space_can_be_freed,
                        Formatter.formatShortFileSize(requireContext(), state.totalSize)
                    )
                    append("\n")
                    append(getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, state.totalItems))
                }
            } else if (!state.isReady) {
                bottomBarText.text = getString(easterEggProgressMsg)
            } else {
                bottomBarText.text = ""
            }

            bottomBar.menu?.findItem(R.id.menu_action_upgrade)?.let {
                it.isVisible = state.upgradeInfo?.isPro != true
            }

            mainAction.isInvisible = !state.isReady
            mainAction.isEnabled = state.actionState != DashboardViewModel.BottomBarState.Action.WORKING

            mainAction.setOnClickListener {
                if (state.actionState == DashboardViewModel.BottomBarState.Action.DELETE) {
                    MaterialAlertDialogBuilder(requireContext()).apply {
                        setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                        setMessage(R.string.dashboard_delete_all_message)
                        setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ ->
                            vm.mainAction(
                                state.actionState
                            )
                        }
                        setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    }.show()
                } else {
                    vm.mainAction(state.actionState)
                }
            }

            when (state.actionState) {
                DashboardViewModel.BottomBarState.Action.SCAN -> {
                    mainAction.setImageResource(R.drawable.ic_layer_search_24)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(com.google.android.material.R.attr.colorOnPrimaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(com.google.android.material.R.attr.colorPrimaryContainer))
                }

                DashboardViewModel.BottomBarState.Action.DELETE -> {
                    mainAction.setImageResource(R.drawable.ic_baseline_delete_sweep_24)
                    mainAction.imageTintList = ColorStateList.valueOf(
                        requireContext().getColorForAttr(com.google.android.material.R.attr.colorOnError)
                    )
                    mainAction.backgroundTintList = ColorStateList.valueOf(
                        requireContext().getColorForAttr(androidx.appcompat.R.attr.colorError)
                    )
                }

                DashboardViewModel.BottomBarState.Action.ONECLICK -> {
                    mainAction.setImageResource(R.drawable.ic_delete_alert_24)
                    mainAction.imageTintList = ColorStateList.valueOf(
                        requireContext().getColorForAttr(com.google.android.material.R.attr.colorOnError)
                    )
                    mainAction.backgroundTintList = ColorStateList.valueOf(
                        requireContext().getColorForAttr(androidx.appcompat.R.attr.colorError)
                    )
                }

                DashboardViewModel.BottomBarState.Action.WORKING -> {
                    mainAction.setImageDrawable(null)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(com.google.android.material.R.attr.colorSecondaryContainer))
                }

                DashboardViewModel.BottomBarState.Action.WORKING_CANCELABLE -> {
                    mainAction.setImageResource(R.drawable.ic_cancel)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(com.google.android.material.R.attr.colorOnTertiaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(com.google.android.material.R.attr.colorTertiaryContainer))
                }
            }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is DashboardEvents.CorpseFinderDeleteConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(R.string.corpsefinder_delete_all_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ -> vm.confirmCorpseDeletion() }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ -> vm.showCorpseFinderDetails() }
                }.show()

                is DashboardEvents.SystemCleanerDeleteConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(R.string.systemcleaner_delete_all_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ -> vm.confirmFilterContentDeletion() }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ -> vm.showSystemCleanerDetails() }
                }.show()

                is DashboardEvents.AppCleanerDeleteConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(R.string.appcleaner_delete_all_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ -> vm.confirmAppJunkDeletion() }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ -> vm.showAppCleanerDetails() }
                }.show()

                DashboardEvents.SetupDismissHint -> {
                    Snackbar
                        .make(
                            requireView(),
                            R.string.setup_dismiss_hint,
                            Snackbar.LENGTH_LONG
                        )
                        .setAnchorView(ui.mainAction)
                        .setAction(eu.darken.sdmse.common.R.string.general_undo_action) { _ -> vm.undoSetupHide() }
                        .show()
                }

                is DashboardEvents.TaskResult -> Snackbar
                    .make(
                        requireView(),
                        event.result.primaryInfo.get(requireContext()),
                        Snackbar.LENGTH_LONG
                    )
                    .setAnchorView(ui.mainAction)
                    .show()

                DashboardEvents.TodoHint -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(eu.darken.sdmse.common.R.string.general_todo_msg)
                }.show()
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
