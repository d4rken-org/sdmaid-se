package eu.darken.sdmse.main.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
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
        ui.toolbar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_settings -> {
                        doNavigate(DashboardFragmentDirections.actionDashboardFragmentToSettingsContainerFragment())
                        true
                    }
                    else -> super.onOptionsItemSelected(it)
                }
            }
            subtitle = "Buildtype: ${BuildConfigWrap.BUILD_TYPE}"
        }

        ui.list.setupDefaults(dashAdapter, dividers = false)

        vm.listItems.observe2(ui) {
            dashAdapter.update(it)
        }

        vm.dashboardevents.observe2(ui) {
            when (it) {

            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
