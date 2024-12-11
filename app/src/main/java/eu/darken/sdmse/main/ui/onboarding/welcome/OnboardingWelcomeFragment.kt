package eu.darken.sdmse.main.ui.onboarding.welcome

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.pkgs.getPackageInfo2
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.OnboardingWelcomeFragmentBinding

@AndroidEntryPoint
class OnboardingWelcomeFragment : Fragment3(R.layout.onboarding_welcome_fragment) {

    override val vm: OnboardingWelcomeViewModel by viewModels()
    override val ui: OnboardingWelcomeFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            whole(ui.root)
        }

        ui.goAction.setOnClickListener {
            val legacySdm = requireContext().packageManager.getPackageInfo2("eu.thedarken.sdm".toPkgId(), 0)
            if (legacySdm != null) {
                OnboardingWelcomeFragmentDirections.actionOnboardingWelcomeFragmentToVersusSetupFragment()
                    .navigate()
            } else {
                OnboardingWelcomeFragmentDirections.actionOnboardingWelcomeFragmentToOnboardingPrivacyFragment()
                    .navigate()
            }
        }

        ui.betaHint.isGone = BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE
        super.onViewCreated(view, savedInstanceState)
    }

}
