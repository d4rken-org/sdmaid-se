package eu.darken.sdmse.main.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.OnboardingFragmentBinding

@AndroidEntryPoint
class OnboardingFragment : Fragment3(R.layout.onboarding_fragment) {

    override val vm: OnboardingFragmentVM by viewModels()
    override val ui: OnboardingFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.goAction.setOnClickListener { vm.finishOnboarding() }
        ui.privacyPolicy.setOnClickListener { vm.goPrivacyPolicy() }
        super.onViewCreated(view, savedInstanceState)
    }

}
