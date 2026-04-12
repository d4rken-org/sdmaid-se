package eu.darken.sdmse.main.ui.onboarding.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Campaign
import androidx.compose.material.icons.twotone.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun OnboardingPrivacyScreenHost(
    vm: OnboardingPrivacyViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    OnboardingPrivacyScreen(
        stateSource = vm.state,
        onContinue = vm::onContinue,
        onPrivacyPolicy = vm::goPrivacyPolicy,
        onToggleMotd = vm::toggleMotd,
        onToggleUpdateCheck = vm::toggleUpdateCheck,
    )
}

@Composable
internal fun OnboardingPrivacyScreen(
    stateSource: Flow<OnboardingPrivacyViewModel.State> = flowOf(
        OnboardingPrivacyViewModel.State(
            isMotdEnabled = true,
            isUpdateCheckEnabled = true,
            isUpdateCheckSupported = true,
        )
    ),
    onContinue: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
    onToggleMotd: () -> Unit = {},
    onToggleUpdateCheck: () -> Unit = {},
) {
    val state = stateSource.collectAsStateWithLifecycle(
        initialValue = OnboardingPrivacyViewModel.State(
            isMotdEnabled = true,
            isUpdateCheckEnabled = true,
            isUpdateCheckSupported = false,
        )
    )

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.onboarding_privacy_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.onboarding_privacy_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = onPrivacyPolicy) {
                Text(stringResource(R.string.onboarding_privacy_policy_action))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Update check toggle
            if (state.value.isUpdateCheckSupported) {
                ToggleRow(
                    icon = { Icon(Icons.TwoTone.SystemUpdate, contentDescription = null) },
                    title = stringResource(R.string.onboarding_privacy_update_check_title),
                    description = stringResource(R.string.onboarding_privacy_update_check_body),
                    checked = state.value.isUpdateCheckEnabled,
                    onToggle = onToggleUpdateCheck,
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // MOTD toggle
            ToggleRow(
                icon = { Icon(Icons.TwoTone.Campaign, contentDescription = null) },
                title = stringResource(R.string.onboarding_privacy_motd_title),
                description = stringResource(R.string.onboarding_privacy_motd_body),
                checked = state.value.isMotdEnabled,
                onToggle = onToggleMotd,
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                Text(stringResource(R.string.general_continue_action))
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
