package eu.darken.sdmse.common.upgrade.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton

internal object UpgradeScreenTags {
    const val LOADING = "upgrade_loading"
    const val ACTIONS = "upgrade_actions"
    const val MASCOT_HAPPY = "upgrade_mascot_happy"
    const val MASCOT_GRUMPY = "upgrade_mascot_grumpy"
    const val FOSS_SPONSOR = "upgrade_foss_sponsor"
    const val FOSS_STATUS_FREE = "upgrade_foss_status_free"
    const val FOSS_STATUS_UPGRADED = "upgrade_foss_status_upgraded"
    const val FOSS_SHOW_OPTIONS = "upgrade_foss_show_options"
    const val FOSS_DONATE = "upgrade_foss_donate"
    const val GPLAY_SUBSCRIPTION = "upgrade_gplay_subscription"
    const val GPLAY_IAP = "upgrade_gplay_iap"
    const val GPLAY_RESTORE = "upgrade_gplay_restore"
    const val GPLAY_RESTORE_BANNER = "upgrade_gplay_restore_banner"
    const val GPLAY_RESTORE_BANNER_ACTION = "upgrade_gplay_restore_banner_action"
    const val GPLAY_UNAVAILABLE = "upgrade_gplay_unavailable"
    const val GPLAY_RETRY = "upgrade_gplay_retry"
    const val GPLAY_OWNED_HERO = "upgrade_gplay_owned_hero"
    const val GPLAY_OWNED_IAP = "upgrade_gplay_owned_iap"
    const val GPLAY_OWNED_SUB = "upgrade_gplay_owned_sub"
    const val GPLAY_MANAGE_SUB = "upgrade_gplay_manage_sub"
    const val GPLAY_GRACE = "upgrade_gplay_grace"
    const val GPLAY_GRACE_SPINNER = "upgrade_gplay_grace_spinner"
    const val GPLAY_GRACE_RESTORE = "upgrade_gplay_grace_restore"
}

// Composed app title with the flavor postfix highlighted in the upgraded color while Pro is
// active — the same treatment the dashboard title card uses.
@Composable
internal fun upgradeScreenTitle(upgraded: Boolean): AnnotatedString = buildAnnotatedString {
    append(stringResource(CommonR.string.app_name))
    append(" ")
    if (upgraded) pushStyle(SpanStyle(color = colorResource(R.color.colorUpgraded)))
    append(stringResource(R.string.app_name_upgrade_postfix))
    if (upgraded) pop()
}

@Composable
internal fun UpgradeScreenScaffold(
    @StringRes titleRes: Int,
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) = UpgradeScreenScaffold(
    title = AnnotatedString(stringResource(titleRes)),
    onNavigateUp = onNavigateUp,
    snackbarHostState = snackbarHostState,
    content = content,
)

@Composable
internal fun UpgradeScreenScaffold(
    title: AnnotatedString,
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)

    SdmScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(it) }
        },
        content = content,
    )
}

@Composable
internal fun UpgradeScreenContent(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
internal fun UpgradeMascot(
    size: Dp,
    modifier: Modifier = Modifier,
    happy: Boolean = true,
) {
    Image(
        painter = painterResource(if (happy) R.drawable.sdm_happy else R.drawable.sdm_not_happy),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .testTag(if (happy) UpgradeScreenTags.MASCOT_HAPPY else UpgradeScreenTags.MASCOT_GRUMPY),
    )
}

@Composable
internal fun UpgradeHeader(
    mascotSize: Dp,
    modifier: Modifier = Modifier,
    happy: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
            shape = CircleShape,
        ) {
            UpgradeMascot(
                size = mascotSize,
                modifier = Modifier.padding(16.dp),
                happy = happy,
            )
        }
    }
}

@Composable
internal fun UpgradePreambleCard(
    text: String,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.elevatedCardColors(),
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Composable
internal fun UpgradeSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    colors: CardColors? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColors = colors ?: CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpgradeSectionHeader(
                title = title,
                icon = icon,
                iconTint = iconTint,
                leading = leading,
            )
            content()
        }
    }
}

// The icon+title header every section card leads with — also usable standalone so headerless
// cards (like the offers action card) can join the same visual pattern.
@Composable
internal fun UpgradeSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) {
            leading()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.primary else iconTint,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun UpgradeSectionBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun UpgradeFeatureList(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                if (line.startsWith("•")) {
                    UpgradeFeatureRow(text = line.removePrefix("•").trim())
                } else {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

@Composable
private fun UpgradeFeatureRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.TwoTone.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun UpgradeHintText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun UpgradeActionCard(
    modifier: Modifier = Modifier,
    colors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColors = colors ?: CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun UpgradeLoadingBlock(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
            .testTag(UpgradeScreenTags.LOADING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(eu.darken.sdmse.common.R.string.general_progress_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun UpgradeInlineStateCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    UpgradeSectionCard(
        title = title,
        icon = icon,
        modifier = modifier.testTag(UpgradeScreenTags.GPLAY_UNAVAILABLE),
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        content()
    }
}
