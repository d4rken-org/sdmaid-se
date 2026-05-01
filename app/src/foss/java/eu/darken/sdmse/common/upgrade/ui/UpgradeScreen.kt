package eu.darken.sdmse.common.upgrade.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Favorite
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import androidx.compose.ui.unit.dp

@Composable
fun UpgradeScreenHost(
    vm: UpgradeViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sponsorReturnTracker = remember { SponsorReturnTracker() }

    LaunchedEffect(Unit) {
        vm.snackbarEvents.collect { stringRes ->
            snackbarHostState.showSnackbar(context.getString(stringRes))
        }
    }

    LaunchedEffect(Unit) {
        vm.toastEvents.collect { stringRes ->
            Toast.makeText(context, context.getString(stringRes), Toast.LENGTH_LONG).show()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        sponsorReturnTracker.onStop()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (sponsorReturnTracker.consumeResumeReturn()) {
            vm.checkSponsorReturn()
        }
    }

    UpgradeScreen(
        snackbarHostState = snackbarHostState,
        onGithubSponsors = vm::goGithubSponsors,
        onNavigateUp = vm::navUp,
    )
}

@Composable
internal fun UpgradeScreen(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onGithubSponsors: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    UpgradeScreenScaffold(
        titleRes = R.string.upgrade_screen_title,
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
    ) { paddingValues ->
        UpgradeScreenContent(
            paddingValues = paddingValues,
        ) {
            UpgradeHeader(
                mascotSize = 104.dp,
            )

            UpgradePreambleCard(
                text = stringResource(R.string.upgrade_screen_preamble),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )

            UpgradeSectionCard(
                title = stringResource(R.string.upgrade_screen_why_title),
                icon = Icons.TwoTone.AutoAwesome,
            ) {
                UpgradeFeatureList(text = stringResource(R.string.upgrade_screen_why_body))
            }

            UpgradeSectionCard(
                title = stringResource(R.string.upgrade_screen_how_title),
                icon = Icons.TwoTone.Favorite,
            ) {
                UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_how_body))
            }

            UpgradeActionCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Button(
                    onClick = onGithubSponsors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UpgradeScreenTags.FOSS_SPONSOR),
                ) {
                    Text(stringResource(R.string.upgrade_screen_sponsor_action))
                }

                UpgradeHintText(text = stringResource(R.string.upgrade_screen_sponsor_action_hint))
            }
        }
    }
}

internal class SponsorReturnTracker {
    private var wentToBackground = false

    fun onStop() {
        wentToBackground = true
    }

    fun consumeResumeReturn(): Boolean {
        return if (wentToBackground) {
            wentToBackground = false
            true
        } else {
            false
        }
    }
}

@Preview2
@Composable
private fun UpgradeScreenPreview() {
    PreviewWrapper {
        UpgradeScreen()
    }
}
