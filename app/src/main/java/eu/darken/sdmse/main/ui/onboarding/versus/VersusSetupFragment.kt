package eu.darken.sdmse.main.ui.onboarding.versus

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.OnboardingVersusFragmentBinding

@AndroidEntryPoint
class VersusSetupFragment : Fragment3(R.layout.onboarding_versus_fragment) {

    override val vm: VersusSetupViewModel by viewModels()
    override val ui: OnboardingVersusFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, top = true, left = true, right = true, bottom = true)
        }

        ui.goAction.setOnClickListener {
            VersusSetupFragmentDirections.actionVersusSetupFragmentToOnboardingPrivacyFragment().navigate()
        }
        super.onViewCreated(view, savedInstanceState)
    }

}
