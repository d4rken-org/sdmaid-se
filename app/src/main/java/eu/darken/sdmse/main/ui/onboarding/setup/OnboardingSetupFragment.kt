package eu.darken.sdmse.main.ui.onboarding.setup

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.OnboardingSetupFragmentBinding

@AndroidEntryPoint
class OnboardingSetupFragment : Fragment3(R.layout.onboarding_setup_fragment) {

    override val vm: OnboardingSetupViewModel by viewModels()
    override val ui: OnboardingSetupFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            whole(ui.root)
        }

        ui.goAction.setOnClickListener { vm.finishOnboarding() }
        super.onViewCreated(view, savedInstanceState)
    }

}
