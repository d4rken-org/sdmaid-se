package eu.darken.sdmse.setup

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.SetupFragmentBinding
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.saf.SafGrantPrimaryContract
import javax.inject.Inject

@AndroidEntryPoint
class SetupFragment : Fragment3(R.layout.setup_fragment) {

    override val vm: SetupFragmentVM by viewModels()
    override val ui: SetupFragmentBinding by viewBinding()

    @Inject lateinit var setupAdapter: SetupAdapter
    @Inject lateinit var webpageTool: WebpageTool

    private lateinit var safRequestLauncher: ActivityResultLauncher<SAFSetupModule.State.PathAccess>
    private lateinit var runtimePermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        safRequestLauncher = registerForActivityResult(SafGrantPrimaryContract()) {
            vm.onSafAccessGranted(it)
        }
        runtimePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.onRuntimePermissionGranted(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_help -> {
                        // TODO
                        true
                    }
                    else -> super.onOptionsItemSelected(it)
                }
            }
        }

        ui.list.setupDefaults(setupAdapter, dividers = false)

        vm.listItems.observe2(ui) {
            setupAdapter.update(it)
        }

        vm.events.observe2(ui) {
            when (it) {
                is SetupEvents.SafRequestAccess -> safRequestLauncher.launch(it.item)
                is SetupEvents.SafWrongPathError -> {
                    Snackbar.make(requireView(), R.string.setup_saf_error_wrong_path, Snackbar.LENGTH_LONG)
                        .setAction(R.string.general_help_action) {
                            webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Setup#storage-access-framework")
                        }
                        .show()
                }
                is SetupEvents.RuntimePermissionRequests -> {

                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
}
