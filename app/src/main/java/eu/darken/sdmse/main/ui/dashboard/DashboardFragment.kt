package eu.darken.sdmse.main.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.navigation.doNavigate
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.DashboardFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment3(R.layout.dashboard_fragment) {

    override val vm: DashboardFragmentVM by viewModels()
    override val ui: DashboardFragmentBinding by viewBinding()

    @Inject lateinit var dashAdapter: DashboardAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.list.setupDefaults(dashAdapter, dividers = false)

        vm.listItems.observe2(ui) {
            dashAdapter.update(it)
        }

        ui.bottomAppBar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_action_upgrade -> {
                        doNavigate(DashboardFragmentDirections.actionDashboardFragmentToUpgradeFragment())
                        true
                    }
                    R.id.menu_action_settings -> {
                        doNavigate(DashboardFragmentDirections.actionDashboardFragmentToSettingsContainerFragment())
                        true
                    }
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }
        vm.bottomBarState.observe2(ui) { state ->
            bottomBarText.text = state.leftInfo?.get(requireContext())
            bottomAppBar.menu?.findItem(R.id.menu_action_upgrade)?.let {
                it.isVisible = state.upgradeInfo?.isPro != true
            }

            mainAction.isEnabled = state.actionState != DashboardFragmentVM.BottomBarState.Action.WORKING

            mainAction.setOnClickListener { vm.mainAction(state.actionState) }

            when (state.actionState) {
                DashboardFragmentVM.BottomBarState.Action.SCAN -> {
                    mainAction.setImageResource(R.drawable.ic_layer_search_24)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorOnPrimaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorPrimaryContainer))
                }
                DashboardFragmentVM.BottomBarState.Action.DELETE -> {
                    mainAction.setImageResource(R.drawable.ic_baseline_delete_sweep_24)
                    mainAction.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    mainAction.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.red)
                    )
                }
                DashboardFragmentVM.BottomBarState.Action.WORKING -> {
                    mainAction.setImageDrawable(null)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorOnSecondaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorSecondaryContainer))
                }
                DashboardFragmentVM.BottomBarState.Action.WORKING_CANCELABLE -> {
                    mainAction.setImageResource(R.drawable.ic_cancel)
                    mainAction.imageTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorOnTertiaryContainer))
                    mainAction.backgroundTintList =
                        ColorStateList.valueOf(getColorForAttr(R.attr.colorTertiaryContainer))
                }
            }
        }

        vm.dashboardevents.observe2(ui) { event ->
            when (event) {
                is DashboardEvents.CorpseFinderDeleteAllConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(
                        getString(
                            R.string.corpsefinder_delete_all_confirmation_message,
                            requireContext().getQuantityString2(R.plurals.result_x_items, event.corpses.size)
                        )
                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ -> vm.confirmCorpseDeletion(event.corpses) }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ -> vm.showCorpseFinderDetails() }
                }.show()
                is DashboardEvents.SystemCleanerDeleteAllConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(
                        getString(
                            R.string.systemcleaner_delete_all_confirmation_message,
                            requireContext().getQuantityString2(
                                R.plurals.result_x_items,
                                event.sieves.sumOf { it.items.size })
                        )
                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ -> vm.confirmFilterContentDeletion() }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ -> vm.showSystemCleanerDetails() }
                }.show()
                is DashboardEvents.AppCleanerDeleteAllConfirmation -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.general_delete_confirmation_title)
                    setMessage(
                        getString(
                            R.string.appcleaner_delete_all_confirmation_message,
                            requireContext().getQuantityString2(R.plurals.result_x_items, event.appJunks.size)
                        )
                    )
                    setPositiveButton(R.string.general_delete_action) { _, _ -> vm.confirmAppJunkDeletion(event.appJunks) }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                    setNeutralButton(R.string.general_show_details_action) { _, _ -> vm.showAppCleanerDetails() }
                }.show()
                DashboardEvents.SetupDismissHint -> {
                    Snackbar
                        .make(
                            requireView(),
                            R.string.setup_dismiss_hint,
                            Snackbar.LENGTH_LONG
                        )
                        .setAction(R.string.general_undo_action) { _ -> vm.undoSetupHide() }
                        .show()
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
