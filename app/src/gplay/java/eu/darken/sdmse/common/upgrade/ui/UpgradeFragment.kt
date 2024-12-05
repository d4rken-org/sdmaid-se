package eu.darken.sdmse.common.upgrade.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdge
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.UpgradeFragmentBinding

@AndroidEntryPoint
class UpgradeFragment : Fragment3(R.layout.upgrade_fragment) {

    override val vm: UpgradeViewModel by viewModels()
    override val ui: UpgradeFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdge().apply {
            topHalf(ui.toolbar)
            bottomHalf(ui.scrollView)
        }

        ui.toolbar.setupWithNavController(findNavController())

        vm.state.observe2(ui) { state ->
            val iapOffer = state.iap?.details?.oneTimePurchaseOfferDetails
            val subOffer = state.sub?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
                OurSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offer)
            }
            val subOfferTrial = state.sub?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
                OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offer)
            }

            breakEvenInfo.text = getString(R.string.upgrade_screen_how_body)

            upgradeIapAction.apply {
                isEnabled = iapOffer != null && !state.hasIap
                setOnClickListener { vm.onGoIap(requireActivity()) }
                isVisible = true
            }
            upgradeIapHint.apply {
                val iapPrice = iapOffer?.formattedPrice
                text = getString(R.string.upgrade_screen_iap_action_hint, "$iapPrice")
                isVisible = iapOffer != null
            }

            val canSub = subOffer != null || subOfferTrial != null
            upgradeSubAction.apply {
                isEnabled = canSub && !state.hasSub
                when {
                    subOfferTrial != null -> {
                        text = getString(R.string.upgrade_screen_subscription_trial_action)
                        setOnClickListener { vm.onGoSubscriptionTrial(requireActivity()) }
                    }

                    subOffer != null -> {
                        text = getString(R.string.upgrade_screen_subscription_action)
                        setOnClickListener { vm.onGoSubscription(requireActivity()) }
                    }

                    else -> log(TAG) { "No sub available" }
                }
                isVisible = true
            }
            upgradeSubHint.apply {
                val subPrice = subOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                text = getString(R.string.upgrade_screen_subscription_action_hint, "$subPrice")
                isVisible = canSub
            }

            actionBox.isVisible = true
            actionProgress.isGone = true

            restoreAction.setOnClickListener {
                vm.restorePurchase()
            }
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                UpgradeEvents.RestoreFailed -> MaterialAlertDialogBuilder(requireContext()).apply {
                    setMessage(
                        """
                        ${getString(R.string.upgrade_screen_restore_purchase_message)}
                        
                        ${getString(R.string.upgrade_screen_restore_troubleshooting_msg)}
                        
                        ${getString(R.string.upgrade_screen_restore_sync_patience_hint)}  
                        
                        ${getString(R.string.upgrade_screen_restore_multiaccount_hint)}
                        """.trimIndent()
                    )
                    setPositiveButton(eu.darken.sdmse.common.R.string.general_dismiss_action) { _, _ ->

                    }
                }.show()
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("Upgrade", "Gplay", "Fragment")
    }
}
