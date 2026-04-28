package eu.darken.sdmse.main.ui.onboarding.privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.NotificationImportant
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.ui.R as UiR

@Composable
fun OnboardingPrivacyScreenHost(
    vm: OnboardingPrivacyViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    OnboardingPrivacyScreen(
        state = state,
        onContinue = vm::onContinue,
        onPrivacyPolicy = vm::goPrivacyPolicy,
        onToggleMotd = vm::toggleMotd,
        onToggleUpdateCheck = vm::toggleUpdateCheck,
    )
}

@Composable
internal fun OnboardingPrivacyScreen(
    state: OnboardingPrivacyViewModel.State = OnboardingPrivacyViewModel.State(
        isMotdEnabled = true,
        isUpdateCheckEnabled = true,
        isUpdateCheckSupported = true,
    ),
    onContinue: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
    onToggleMotd: () -> Unit = {},
    onToggleUpdateCheck: () -> Unit = {},
) {
    var isVisible by remember { mutableStateOf(false) }
    val continueButtonFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { isVisible = true }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = tween(400),
                ),
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Image(
                        painter = painterResource(UiR.drawable.splash_mascot),
                        contentDescription = null,
                        modifier = Modifier.size(128.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.onboarding_privacy_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.onboarding_privacy_body1),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onPrivacyPolicy,
                    ) {
                        Text(stringResource(R.string.settings_privacy_policy_label))
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (state.isUpdateCheckSupported) {
                        ToggleRow(
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            title = stringResource(R.string.updatecheck_setting_enabled_label),
                            description = stringResource(R.string.updatecheck_setting_enabled_explanation),
                            checked = state.isUpdateCheckEnabled,
                            onToggle = onToggleUpdateCheck,
                        )
                    }

                    ToggleRow(
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.NotificationImportant,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        title = stringResource(R.string.motd_setting_enabled_label),
                        description = stringResource(R.string.motd_setting_enabled_explanation),
                        checked = state.isMotdEnabled,
                        onToggle = onToggleMotd,
                    )
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 400, delayMillis = 200)),
            ) {
                LaunchedEffect(Unit) { continueButtonFocusRequester.requestFocus() }
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(continueButtonFocusRequester)
                        .padding(horizontal = 32.dp)
                        .padding(top = 16.dp, bottom = 16.dp),
                ) {
                    Text(stringResource(R.string.onboarding_welcome_continue_action))
                }
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
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview2
@Composable
private fun OnboardingPrivacyScreenPreview() {
    PreviewWrapper {
        OnboardingPrivacyScreen()
    }
}
