package eu.darken.sdmse.main.ui.onboarding.privacy

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.setChecked2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.OnboardingPrivacyFragmentBinding

@AndroidEntryPoint
class OnboardingPrivacyFragment : Fragment3(R.layout.onboarding_privacy_fragment) {

    override val vm: OnboardingPrivacyViewModel by viewModels()
    override val ui: OnboardingPrivacyFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            whole(ui.root)
        }

        ui.goAction.setOnClickListener {
            OnboardingPrivacyFragmentDirections.actionOnboardingPrivacyFragmentToOnboardingSetupFragment().navigate()
        }

        ui.privacyPolicyAction.setOnClickListener { vm.goPrivacyPolicy() }

        ui.motdContainer.setOnClickListener { vm.toggleMotd() }
        ui.updateContainer.setOnClickListener { vm.toggleUpdateCheck() }

        vm.state.observe2(ui) { state ->
            motdToggle.setChecked2(state.isMotdEnabled, false)
            updateToggle.setChecked2(state.isUpdateCheckEnabled, false)
            updateContainer.isVisible = state.isUpdateCheckSupported
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
