package eu.darken.sdmse.common.upgrade.ui

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Verified
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import androidx.compose.ui.unit.dp

// Which presentation the FOSS upgrade screen shows: the classic support pitch, or one of the
// status views behind the settings "upgrade status" entry.
internal enum class FossUpgradeView {
    PITCH,
    STATUS_FREE,
    STATUS_UPGRADED,
}

@Composable
fun UpgradeScreenHost(
    route: UpgradeRoute = UpgradeRoute(),
    vm: UpgradeViewModel = hiltViewModel(),
) {
    LaunchedEffect(route) { vm.bindRoute(route) }
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

    val view by vm.state.collectAsStateWithLifecycle()

    UpgradeScreen(
        // Until the route binding lands (one frame): the default route keeps rendering the pitch
        // exactly as before, only the manage route waits for the status decision.
        view = view ?: FossUpgradeView.PITCH.takeIf { !route.manage },
        snackbarHostState = snackbarHostState,
        onGithubSponsors = vm::goGithubSponsors,
        onShowUpgradeOptions = vm::onShowUpgradeOptions,
        onNavigateUp = vm::navUp,
    )
}

@Composable
internal fun UpgradeScreen(
    view: FossUpgradeView? = FossUpgradeView.PITCH,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onGithubSponsors: () -> Unit = {},
    onShowUpgradeOptions: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    UpgradeScreenScaffold(
        // Status views describe the existing install, not a support ask — they get the composed
        // flavor title, with the postfix highlighted for supporters like the dashboard does it.
        title = if (view == FossUpgradeView.PITCH) {
            AnnotatedString(stringResource(R.string.upgrade_screen_title))
        } else {
            upgradeScreenTitle(upgraded = view == FossUpgradeView.STATUS_UPGRADED)
        },
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
    ) { paddingValues ->
        when (view) {
            null -> Unit // Route not bound yet (single frame); content lands with the next state.
            FossUpgradeView.PITCH -> UpgradePitchContent(
                paddingValues = paddingValues,
                onGithubSponsors = onGithubSponsors,
            )

            FossUpgradeView.STATUS_FREE -> UpgradeStatusFreeContent(
                paddingValues = paddingValues,
                onShowUpgradeOptions = onShowUpgradeOptions,
            )

            FossUpgradeView.STATUS_UPGRADED -> UpgradeStatusUpgradedContent(
                paddingValues = paddingValues,
                onGithubSponsors = onGithubSponsors,
            )
        }
    }
}

@Composable
private fun UpgradePitchContent(
    paddingValues: PaddingValues,
    onGithubSponsors: () -> Unit,
) {
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

@Composable
private fun UpgradeStatusFreeContent(
    paddingValues: PaddingValues,
    onShowUpgradeOptions: () -> Unit,
) {
    UpgradeScreenContent(
        paddingValues = paddingValues,
    ) {
        UpgradeHeader(
            mascotSize = 104.dp,
        )

        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_status_free_title),
            icon = Icons.TwoTone.Info,
            modifier = Modifier.testTag(UpgradeScreenTags.FOSS_STATUS_FREE),
        ) {
            UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_status_free_body))
            Button(
                onClick = onShowUpgradeOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS),
            ) {
                Text(stringResource(R.string.upgrade_screen_status_free_action))
            }
        }
    }
}

@Composable
private fun UpgradeStatusUpgradedContent(
    paddingValues: PaddingValues,
    onGithubSponsors: () -> Unit,
) {
    UpgradeScreenContent(
        paddingValues = paddingValues,
    ) {
        UpgradeHeader(
            mascotSize = 104.dp,
        )

        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_status_upgraded_title),
            icon = Icons.TwoTone.Verified,
            modifier = Modifier.testTag(UpgradeScreenTags.FOSS_STATUS_UPGRADED),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text(
                text = stringResource(R.string.upgrade_screen_status_upgraded_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_recurring_title),
            icon = Icons.TwoTone.Favorite,
        ) {
            UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_recurring_body))
            OutlinedButton(
                onClick = onGithubSponsors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.FOSS_DONATE),
            ) {
                Text(stringResource(R.string.upgrade_screen_recurring_action))
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

@Preview2
@Composable
private fun UpgradeScreenStatusFreePreview() {
    PreviewWrapper {
        UpgradeScreen(view = FossUpgradeView.STATUS_FREE)
    }
}

@Preview2
@Composable
private fun UpgradeScreenStatusUpgradedPreview() {
    PreviewWrapper {
        UpgradeScreen(view = FossUpgradeView.STATUS_UPGRADED)
    }
}
