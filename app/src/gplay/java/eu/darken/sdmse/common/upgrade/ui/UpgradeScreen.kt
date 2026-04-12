package eu.darken.sdmse.common.upgrade.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.upgrade.core.OurSku

@Composable
fun UpgradeScreenHost(
    vm: UpgradeViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Handle restore failed events
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UpgradeEvents.RestoreFailed -> activity?.let {
                    MaterialAlertDialogBuilder(it).apply {
                        setMessage(
                            buildString {
                                appendLine(it.getString(R.string.upgrade_screen_restore_purchase_message))
                                appendLine()
                                appendLine(it.getString(R.string.upgrade_screen_restore_troubleshooting_msg))
                                appendLine()
                                appendLine(it.getString(R.string.upgrade_screen_restore_sync_patience_hint))
                                appendLine()
                                appendLine(it.getString(R.string.upgrade_screen_restore_multiaccount_hint))
                            }
                        )
                        setPositiveButton(eu.darken.sdmse.common.R.string.general_dismiss_action) { _, _ -> }
                    }.show()
                }
            }
        }
    }

    val pricing = vm.state.collectAsStateWithLifecycle(initialValue = null)

    UpgradeScreen(
        pricing = pricing.value,
        onIap = { activity?.let { vm.onGoIap(it) } },
        onSubscription = { activity?.let { vm.onGoSubscription(it) } },
        onSubscriptionTrial = { activity?.let { vm.onGoSubscriptionTrial(it) } },
        onRestore = vm::restorePurchase,
        onNavigateUp = vm::navUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpgradeScreen(
    pricing: UpgradeViewModel.Pricing? = null,
    onIap: () -> Unit = {},
    onSubscription: () -> Unit = {},
    onSubscriptionTrial: () -> Unit = {},
    onRestore: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upgrade_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.upgrade_screen_how_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (pricing != null) {
                val iapOffer = pricing.iap?.details?.oneTimePurchaseOfferDetails
                val subOffer = pricing.sub?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
                    OurSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offer)
                }
                val subOfferTrial = pricing.sub?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
                    OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offer)
                }

                // IAP button
                if (iapOffer != null) {
                    Button(
                        onClick = onIap,
                        enabled = !pricing.hasIap,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.upgrade_screen_iap_action))
                    }
                    Text(
                        text = stringResource(R.string.upgrade_screen_iap_action_hint, iapOffer.formattedPrice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Subscription button
                val canSub = subOffer != null || subOfferTrial != null
                if (canSub) {
                    if (subOfferTrial != null) {
                        OutlinedButton(
                            onClick = onSubscriptionTrial,
                            enabled = !pricing.hasSub,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.upgrade_screen_subscription_trial_action))
                        }
                    } else if (subOffer != null) {
                        OutlinedButton(
                            onClick = onSubscription,
                            enabled = !pricing.hasSub,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.upgrade_screen_subscription_action))
                        }
                    }

                    subOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice?.let { price ->
                        Text(
                            text = stringResource(R.string.upgrade_screen_subscription_action_hint, price),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Restore button
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.upgrade_screen_restore_purchase_action))
                }
            }
        }
    }
}
