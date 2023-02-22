package eu.darken.sdmse.setup

import android.content.Intent
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
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.permissions.Specialpermission
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
    @Inject lateinit var deviceDetective: DeviceDetective

    private lateinit var safRequestLauncher: ActivityResultLauncher<SAFSetupModule.State.PathAccess>
    private var awaitedPermission: Permission? = null
    private lateinit var specialPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var runtimePermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        safRequestLauncher = registerForActivityResult(SafGrantPrimaryContract()) {
            vm.onSafAccessGranted(it)
        }
        runtimePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            vm.onRuntimePermissionsGranted(awaitedPermission, result)
            awaitedPermission = null
        }
        specialPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vm.onRuntimePermissionsGranted(
                awaitedPermission,
                awaitedPermission?.isGranted(requireContext()) ?: true
            )
            awaitedPermission = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.toolbar.apply {
            setupWithNavController(findNavController())
            if (vm.isOnboarding) {
                setNavigationIcon(R.drawable.ic_baseline_close_24)
            }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_help -> {
                        webpageTool.open("https://github.com/d4rken/sdmaid-se/wiki/Setup")
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

        vm.events.observe2(ui) { event ->
            when (event) {
                is SetupEvents.SafRequestAccess -> safRequestLauncher.launch(event.item)
                is SetupEvents.SafWrongPathError -> {
                    Snackbar.make(requireView(), R.string.setup_saf_error_wrong_path, Snackbar.LENGTH_LONG)
                        .setAction(R.string.general_help_action) {
                            webpageTool.open("https://github.com/d4rken/sdmaid-se/wiki/Setup#storage-access-framework")
                        }
                        .show()
                }
                is SetupEvents.RuntimePermissionRequests -> {
                    awaitedPermission = event.item
                    when (event.item) {
                        is Specialpermission -> {
                            try {
                                specialPermissionLauncher.launch(
                                    event.item.createIntent(requireContext(), deviceDetective)
                                )
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Failed to launch permission intent: ${e.asLog()}" }
                                val fallbackIntent = event.item.createIntentFallback(requireContext())
                                if (fallbackIntent == null) {
                                    e.asErrorDialogBuilder(requireContext()).show()
                                    return@observe2
                                }
                                try {
                                    specialPermissionLauncher.launch(fallbackIntent)
                                } catch (e: Exception) {
                                    log(TAG, ERROR) { "Failed to launch FALLBACK intent too :( ${e.asLog()}" }
                                    e.asErrorDialogBuilder(requireContext()).show()
                                }
                            }
                        }
                        else -> runtimePermissionLauncher.launch(event.item.permissionId)
                    }
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Setup", "Fragment")
    }
}
