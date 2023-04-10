package eu.darken.sdmse.main.ui.onboarding.privacy

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.setChecked2
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.OnboardingPrivacyFragmentBinding

@AndroidEntryPoint
class OnboardingPrivacyFragment : Fragment3(R.layout.onboarding_privacy_fragment) {

    override val vm: OnboardingPrivacyFragmentVM by viewModels()
    override val ui: OnboardingPrivacyFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.goAction.setOnClickListener {
            OnboardingPrivacyFragmentDirections.actionOnboardingPrivacyFragmentToOnboardingSetupFragment().navigate()
        }

        ui.privacyPolicyAction.setOnClickListener { vm.goPrivacyPolicy() }
        ui.bugreportingToggle.setOnCheckedChangeListener { _, isChecked -> vm.setBugReportingEnabled(isChecked) }

        var isInitial = true
        vm.isBugReporterEnabled.observe2(ui) { isEnabled ->
            bugreportingToggle.setChecked2(isEnabled, animate = !isInitial)
            isInitial = false
        }
        super.onViewCreated(view, savedInstanceState)
    }

}
