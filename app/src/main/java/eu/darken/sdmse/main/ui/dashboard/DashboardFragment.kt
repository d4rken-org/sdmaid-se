package eu.darken.sdmse.main.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getColorForAttr
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

        ui.mainAction.setOnClickListener {
            vm.triggerMainAction()
        }

        ui.bottomAppBar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_upgrade -> {
                        doNavigate(DashboardFragmentDirections.actionDashboardFragmentToUpgradeFragment())
                        true
                    }
                    R.id.action_settings -> {
                        doNavigate(DashboardFragmentDirections.actionDashboardFragmentToSettingsContainerFragment())
                        true
                    }
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }
        vm.bottomBarState.observe2(ui) {
            bottomBarText.text = it.leftInfo?.get(requireContext())

            mainAction.isEnabled = it.actionState != DashboardFragmentVM.BottomBarState.Action.WORKING

            when (it.actionState) {
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

        vm.dashboardevents.observe2(ui) {
            when (it) {

            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
