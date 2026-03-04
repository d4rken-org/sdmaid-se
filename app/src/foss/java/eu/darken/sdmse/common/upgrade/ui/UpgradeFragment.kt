package eu.darken.sdmse.common.upgrade.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.UpgradeFragmentBinding

@AndroidEntryPoint
class UpgradeFragment : Fragment3(R.layout.upgrade_fragment) {

    override val vm: UpgradeViewModel by viewModels()
    override val ui: UpgradeFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.appbarlayout, top = true)
            insetsPadding(ui.scrollView, bottom = true)
        }

        ui.toolbar.setupWithNavController(findNavController())

        ui.upgradeGithubSponsorsAction.setOnClickListener {
            vm.goGithubSponsors()
        }

        vm.snackbarEvents.observe2 { stringRes ->
            Snackbar.make(requireView(), stringRes, Snackbar.LENGTH_LONG).show()
        }

        vm.toastEvents.observe2 { stringRes ->
            Toast.makeText(requireContext(), stringRes, Toast.LENGTH_LONG).show()
        }

        vm.state.observe2 {

        }

        super.onViewCreated(view, savedInstanceState)
    }

    private var wentToBackground = false

    override fun onStop() {
        super.onStop()
        wentToBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (wentToBackground) {
            wentToBackground = false
            vm.checkSponsorReturn()
        }
    }
}
