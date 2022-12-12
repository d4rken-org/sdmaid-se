package eu.darken.sdmse.main.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.log
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
//                        val requestIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
//                        requestIntent.putExtra("android.content.extra.SHOW_ADVANCED", true)
//                        startActivityForResult(requestIntent, 1)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        requireContext().contentResolver.apply {
            takePersistableUriPermission(
                data?.data!!,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            persistedUriPermissions.forEach {
                log { "URI PERM : $it" }
                DocumentFile.fromTreeUri(requireContext(), it.uri)!!.listFiles().forEach {
                    log { "LIST: ${it.uri}" }
                }
            }
        }
    }
}
