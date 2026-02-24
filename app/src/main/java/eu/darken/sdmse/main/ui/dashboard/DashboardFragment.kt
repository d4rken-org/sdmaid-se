package eu.darken.sdmse.main.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.easterEggProgressMsg
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.getColorForAttr
import eu.darken.sdmse.common.navigation.getQuantityString2
import eu.darken.sdmse.common.navigation.getSpanCount
import eu.darken.sdmse.common.theming.Theming
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DashboardFragmentBinding
import eu.darken.sdmse.deduplicator.ui.PreviewDeletionDialog
import eu.darken.sdmse.main.ui.settings.general.OneClickOptionsDialog
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment3(R.layout.dashboard_fragment) {

    override val vm: DashboardViewModel by viewModels()
    override val ui: DashboardFragmentBinding by viewBinding()

    @Inject lateinit var dashAdapter: DashboardAdapter
    @Inject lateinit var oneClickOptions: OneClickOptionsDialog
    @Inject lateinit var previewDialog: PreviewDeletionDialog
    @Inject lateinit var theming: Theming

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.list, top = true)
            insetsPadding(ui.mainAction, bottom = true)
        }

        ui.list.setupDefaults(
            dashAdapter,
            verticalDividers = false,
            fastscroll = false,
            layouter = GridLayoutManager(context, getSpanCount(), GridLayoutManager.VERTICAL, false)
        )

        vm.listState.observe2(ui) { state ->
            mascotOverlay.isVisible = state.items.isEmpty() || state.isEasterEgg
            list.isVisible = state.items.isNotEmpty()
            dashAdapter.update(state.items)
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
                bottomBarTextLeft.apply {
                    text = getQuantityString2(R.plurals.tasks_activity_active_notification_message, state.activeTasks)
                    append("\n")
                    append(getQuantityString2(R.plurals.tasks_activity_queued_notification_message, state.queuedTasks))
                }
            } else if (state.totalItems > 0 || state.totalSize > 0L) {
                bottomBarTextLeft.apply {
                    val (formatted, quantity) = ByteFormatter.formatSize(context, state.totalSize)
                    text = getQuantityString2(
                        eu.darken.sdmse.common.R.plurals.x_space_can_be_freed,
                        quantity,
                        formatted,
                    )
                    append("\n")
                    append(getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, state.totalItems))
                }
            } else if (!state.isReady) {
                bottomBarTextLeft.text = getString(easterEggProgressMsg)
            } else {
                bottomBarTextLeft.text = ""
            }

            bottomBar.findViewById<View>(R.id.menu_action_settings).let {
                it.isFocusable = true
                it.isFocusableInTouchMode = false
                it.nextFocusUpId = R.id.main_action
            }
            bottomBar.menu?.findItem(R.id.menu_action_upgrade)?.let {
                it.isVisible = state.upgradeInfo?.isPro != true
            }

            mainAction.apply {
                isInvisible = !state.isReady
                isEnabled = state.actionState != DashboardViewModel.BottomBarState.Action.WORKING
                ui.mainAction.nextFocusDownId = R.id.menu_action_settings

                setOnClickListener {
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
                setOnLongClickListener {
                    oneClickOptions.show(requireContext())
                    true
                }
            }

            when (state.actionState) {
                DashboardViewModel.BottomBarState.Action.SCAN -> {
                    mainAction.setImageResource(R.drawable.ic_layer_search_24)
                    mainAction.imageTintList = ColorStateList.valueOf(
                        getColorForAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
                    )
                    mainAction.backgroundTintList = ColorStateList.valueOf(
                        getColorForAttr(com.google.android.material.R.attr.colorPrimaryContainer)
                    )
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
                    mainAction.imageTintList = ColorStateList.valueOf(
                        getColorForAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                    )
                    mainAction.backgroundTintList = ColorStateList.valueOf(
                        getColorForAttr(com.google.android.material.R.attr.colorSecondaryContainer)
                    )
                }

                DashboardViewModel.BottomBarState.Action.WORKING_CANCELABLE -> {
                    mainAction.setImageResource(R.drawable.ic_cancel)
                    mainAction.imageTintList = ColorStateList.valueOf(
                        getColorForAttr(com.google.android.material.R.attr.colorOnTertiaryContainer)
                    )
                    mainAction.backgroundTintList = ColorStateList.valueOf(
                        getColorForAttr(com.google.android.material.R.attr.colorTertiaryContainer)
                    )
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
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ -> vm.showCorpseFinder() }
                }.show()

                is DashboardEvents.SystemCleanerDeleteConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(R.string.systemcleaner_delete_all_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ -> vm.confirmFilterContentDeletion() }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ -> vm.showSystemCleaner() }
                }.show()

                is DashboardEvents.AppCleanerDeleteConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(eu.darken.sdmse.common.R.string.general_delete_confirmation_title)
                    setMessage(R.string.appcleaner_delete_all_confirmation_message)
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_delete_action) { _, _ -> vm.confirmAppJunkDeletion() }
                    setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(eu.darken.sdmse.common.R.string.general_show_details_action) { _, _ -> vm.showAppCleaner() }
                }.show()

                is DashboardEvents.DeduplicatorDeleteConfirmation -> previewDialog.show(
                    mode = PreviewDeletionDialog.Mode.All(clusters = event.clusters ?: emptyList()),
                    onPositive = { vm.confirmDeduplicatorDeletion() },
                    onNegative = { },
                    onNeutral = { vm.showDeduplicator() },
                )

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

                is DashboardEvents.TaskResult -> {} // No snackbar, results shown on cards

                is DashboardEvents.TodoHint -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(eu.darken.sdmse.common.R.string.general_todo_msg)
                }.show()

                is DashboardEvents.OpenIntent -> try {
                    startActivity(event.intent)
                } catch (e: ActivityNotFoundException) {
                    e.asErrorDialogBuilder(requireActivity()).show()
                }

                is DashboardEvents.SqueezerSetup -> {
                    DashboardFragmentDirections.actionDashboardFragmentToSqueezerSetupFragment().navigate()
                }

            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val navController = findNavController()
        val curDest = navController.currentDestination
        log(tag) { "onResume(): currentDestination=${curDest?.label} (${curDest?.id?.let { "0x${Integer.toHexString(it)}" } ?: "null"})" }
        if (curDest != null && curDest.id != R.id.dashboardFragment) {
            log(tag, WARN) { "Dashboard resumed but currentDestination is ${curDest.label}, recovering" }
            navController.popBackStack(R.id.dashboardFragment, false)
        }
    }

}
