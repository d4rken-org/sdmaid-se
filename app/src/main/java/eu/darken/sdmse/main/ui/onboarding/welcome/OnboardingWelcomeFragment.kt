package eu.darken.sdmse.main.ui.onboarding.welcome

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.OnboardingWelcomeFragmentBinding

@AndroidEntryPoint
class OnboardingWelcomeFragment : Fragment3(R.layout.onboarding_welcome_fragment) {

    override val vm: OnboardingWelcomeFragmentVM by viewModels()
    override val ui: OnboardingWelcomeFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.goAction.setOnClickListener {
            OnboardingWelcomeFragmentDirections.actionOnboardingWelcomeFragmentToOnboardingPrivacyFragment().navigate()
        }
        super.onViewCreated(view, savedInstanceState)
    }

}
