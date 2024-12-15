package eu.darken.sdmse.main.ui.release

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.BetaGoodbyeFragmentBinding

@AndroidEntryPoint
class BetaGoodbyeFragment : Fragment3(R.layout.beta_goodbye_fragment) {

    override val vm: BetaGoodbyeViewModel by viewModels()
    override val ui: BetaGoodbyeFragmentBinding by viewBinding()

    private val onBackPressedcallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedcallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            whole(ui.root)
        }

        ui.stayBetaAction.setOnClickListener { vm.consentPrerelease(true) }
        ui.optOutAction.setOnClickListener { vm.consentPrerelease(false) }
        ui.alreadyProdContainer.isGone = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE
        ui.stillBetaContainer.isGone = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.BETA
        super.onViewCreated(view, savedInstanceState)
    }
}
