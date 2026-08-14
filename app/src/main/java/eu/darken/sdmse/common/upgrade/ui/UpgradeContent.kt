package eu.darken.sdmse.common.upgrade.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.R as CommonR

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
    const val GPLAY_SUBSCRIPTION_SPINNER = "upgrade_gplay_subscription_spinner"
    const val GPLAY_IAP = "upgrade_gplay_iap"
    const val GPLAY_IAP_SPINNER = "upgrade_gplay_iap_spinner"
    const val GPLAY_RESTORE = "upgrade_gplay_restore"
    const val GPLAY_RESTORE_BANNER = "upgrade_gplay_restore_banner"
    const val GPLAY_RESTORE_BANNER_ACTION = "upgrade_gplay_restore_banner_action"
    const val GPLAY_UNAVAILABLE = "upgrade_gplay_unavailable"
    const val GPLAY_RETRY = "upgrade_gplay_retry"
    const val GPLAY_OWNED_HERO = "upgrade_gplay_owned_hero"
    const val GPLAY_OWNED_IAP = "upgrade_gplay_owned_iap"
    const val GPLAY_OWNED_SUB = "upgrade_gplay_owned_sub"
    const val GPLAY_MANAGE_SUB = "upgrade_gplay_manage_sub"
    const val GPLAY_PENDING = "upgrade_gplay_pending"
    const val GPLAY_GRACE = "upgrade_gplay_grace"
    const val GPLAY_GRACE_SPINNER = "upgrade_gplay_grace_spinner"
    const val GPLAY_GRACE_RESTORE = "upgrade_gplay_grace_restore"
}

// The app's brand title, composed through the flavor's title template so translators own the word
// order and punctuation instead of the code assuming "name, space, qualifier".
//
// The two flags are deliberately separate. `includeQualifier` decides whether the tier word is part
// of the title at all (the dashboard drops it while free); `highlightQualifier` only decides whether
// it is colored. The FOSS status-free view needs "SD Maid SE FOSS" in plain text, so it passes
// (true, false) — collapsing these into one flag would silently drop FOSS from that screen.
@Composable
internal fun brandTitle(includeQualifier: Boolean, highlightQualifier: Boolean): AnnotatedString {
    val name = AnnotatedString(stringResource(CommonR.string.app_name))
    if (!includeQualifier) return name

    val qualifier = buildAnnotatedString {
        if (highlightQualifier) pushStyle(SpanStyle(color = colorResource(R.color.colorUpgraded)))
        append(stringResource(R.string.app_name_upgrade_postfix))
        if (highlightQualifier) pop()
    }
    return spliceTitleTemplate(
        formatted = stringResource(
            CommonR.string.app_name_upgraded_template,
            BRAND_TITLE_MARKER,
            BRAND_QUALIFIER_MARKER,
        ),
        name = name,
        qualifier = qualifier,
    )
}

// Same composition for the call sites that need a plain String (the settings components take
// String, not AnnotatedString). Routed through brandTitle so the two forms cannot drift apart.
@Composable
internal fun brandTitleText(includeQualifier: Boolean): String =
    brandTitle(includeQualifier = includeQualifier, highlightQualifier = false).text

// Composed app title with the flavor postfix highlighted in the upgraded color while Pro is
// active — the same treatment the dashboard title card uses.
@Composable
internal fun upgradeScreenTitle(upgraded: Boolean): AnnotatedString = brandTitle(
    // Unconditional: this title names the flavor even when the screen is showing the free state.
    includeQualifier = true,
    highlightQualifier = upgraded,
)

// Marker char for brand-title splicing: formatted into the translated pattern via the normal
// Android format path (so %1$s vs %s, argument reordering, and %% all behave), then replaced
// with the styled brand. U+FFFC (object replacement) cannot occur in a real translation.
internal const val BRAND_TITLE_MARKER = "￼"

// The title template's second slot. U+FFF9 (interlinear annotation anchor) is likewise absent from
// real translations, and being distinct from BRAND_TITLE_MARKER is what lets the splice tell the
// two slots apart after the formatter has reordered them.
internal const val BRAND_QUALIFIER_MARKER = "￹"

internal fun spliceBrandTitle(formatted: String, brand: AnnotatedString): AnnotatedString = buildAnnotatedString {
    var rest = formatted
    var found = false
    while (true) {
        val idx = rest.indexOf(BRAND_TITLE_MARKER)
        if (idx < 0) break
        found = true
        append(rest.substring(0, idx))
        append(brand)
        rest = rest.substring(idx + BRAND_TITLE_MARKER.length)
    }
    append(rest)
    if (!found) {
        // Defensive: a translation that lost its placeholder still shows the brand.
        append(" ")
        append(brand)
    }
}

// Splices the two title slots into an already-formatted template. Stricter than spliceBrandTitle on
// purpose: that one splices a brand into a *sentence*, where a repeated marker is a legitimate (if
// odd) translation. A *title* template has exactly two slots, so anything else is damage — and once
// a slot is missing or doubled the template can no longer tell us the intended order or
// punctuation, which is the whole reason it exists. So a broken template is discarded whole and the
// default title is rebuilt from the parts; patching it up piecewise would emit a title no
// translator wrote.
internal fun spliceTitleTemplate(
    formatted: String,
    name: AnnotatedString,
    qualifier: AnnotatedString,
): AnnotatedString {
    val slots = listOf(
        BRAND_TITLE_MARKER to name,
        BRAND_QUALIFIER_MARKER to qualifier,
    ).map { (marker, value) -> Triple(formatted.indexOf(marker), marker, value) }

    val intact = slots.all { (index, marker, _) ->
        index >= 0 && formatted.indexOf(marker, index + marker.length) < 0
    }
    if (!intact) {
        return buildAnnotatedString {
            append(name)
            append(" ")
            append(qualifier)
        }
    }

    return buildAnnotatedString {
        var cursor = 0
        slots.sortedBy { it.first }.forEach { (index, marker, value) ->
            append(formatted.substring(cursor, index))
            append(value)
            cursor = index + marker.length
        }
        append(formatted.substring(cursor))
    }
}

@Preview2
@Composable
private fun UpgradeScreenTitlePreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = upgradeScreenTitle(upgraded = false))
            Text(text = upgradeScreenTitle(upgraded = true))
        }
    }
}

// All three flag combinations the app actually uses, in one place — the pair (true, false) is the
// one that reads as a mistake at a glance, so seeing it render "SD Maid SE FOSS" in plain text is
// what documents it.
@Preview2
@Composable
private fun BrandTitlePreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = brandTitle(includeQualifier = false, highlightQualifier = false))
            Text(text = brandTitle(includeQualifier = true, highlightQualifier = false))
            Text(text = brandTitle(includeQualifier = true, highlightQualifier = true))
            Text(text = brandTitleText(includeQualifier = true))
        }
    }
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

// Exercises the string-resource overload itself. It uses a shared resource on purpose: the flavors
// title this screen from their own keys, which do not both exist in either merged R.
@Preview2
@Composable
private fun UpgradeScreenScaffoldTitleResPreview() {
    PreviewWrapper {
        UpgradeScreenScaffold(
            titleRes = CommonR.string.app_name,
            onNavigateUp = {},
        ) { paddingValues ->
            UpgradeScreenContent(paddingValues = paddingValues) {
                UpgradeSectionBody(text = "Title comes from a string resource.")
            }
        }
    }
}

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

@Preview2
@Composable
private fun UpgradeScreenScaffoldPreview() {
    PreviewWrapper {
        UpgradeScreenScaffold(
            title = AnnotatedString("Support SD Maid"),
            onNavigateUp = {},
        ) { paddingValues ->
            UpgradeScreenContent(paddingValues = paddingValues) {
                UpgradeSectionBody(text = "Scaffold with the screen's content column inside it.")
            }
        }
    }
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

@Preview2
@Composable
private fun UpgradeScreenContentPreview() {
    PreviewWrapper {
        UpgradeScreenContent(paddingValues = PaddingValues(0.dp)) {
            UpgradeHeroCard(text = "The card the content column leads with.")
            UpgradeSectionCard(title = "A section", icon = Icons.TwoTone.AutoAwesome) {
                UpgradeSectionBody(text = "Children are spaced by the column, not by themselves.")
            }
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

// Both faces in one preview: they are picked by the same boolean at every call site, so seeing
// them side by side is what makes an accidental swap obvious.
@Preview2
@Composable
private fun UpgradeMascotPreview() {
    PreviewWrapper {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpgradeMascot(size = 88.dp, happy = true)
            UpgradeMascot(size = 88.dp, happy = false)
        }
    }
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

@Preview2
@Composable
private fun UpgradeHeaderPreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpgradeHeader(mascotSize = 88.dp)
            UpgradeHeader(mascotSize = 88.dp, happy = false)
        }
    }
}

private val HERO_GAP = 16.dp

// Below this much room for the copy the side-by-side split stops paying for itself: measured on a
// 320dp screen at 200% font, the row wrapped the preamble over 10 lines (breaking a word mid-way)
// and came out TALLER than stacking, which needs 6. Scaled by fontScale because the squeeze comes
// from text size as much as from screen width — at 200% font even a normal-width phone must stack.
private val HERO_MIN_TEXT_WIDTH = 150.dp

// The screen opener: mascot and preamble in one card instead of a floating icon stacked on a
// separate text box. Side-by-side keeps the mascot at eye level with the copy it introduces, and
// buys back the vertical space the standalone header used to spend above the fold — but only while
// the copy still has room to breathe, hence the stacked fallback.
@Composable
internal fun UpgradeHeroCard(
    text: String,
    modifier: Modifier = Modifier,
    mascotSize: Dp = 88.dp,
    happy: Boolean = true,
    colors: CardColors = CardDefaults.elevatedCardColors(),
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .padding(end = 8.dp),
        ) {
            val minTextWidth = HERO_MIN_TEXT_WIDTH * LocalDensity.current.fontScale
            if (maxWidth - mascotSize - HERO_GAP < minTextWidth) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UpgradeMascot(
                        size = mascotSize,
                        happy = happy,
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HERO_GAP),
                ) {
                    UpgradeMascot(
                        size = mascotSize,
                        happy = happy,
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// Preview copy matches the shipped preamble in length: the mascot/text split only reads correctly
// if the text wraps like it does in the app.
private const val PREVIEW_PREAMBLE =
    "SD Maid has no ads and doesn't sell user data. Continued development is only made possible by you."

@Preview2
@Composable
private fun UpgradeHeroCardPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeHeroCard(text = PREVIEW_PREAMBLE)
        }
    }
}

// Preview2 only varies light/dark, so it can never reach the stacked branch. These two pin the
// thresholds that flip it: a narrow screen, and a normal-width screen at 200% font.
@Preview(showBackground = true, name = "Compact width", widthDp = 320)
@Preview(showBackground = true, name = "Huge font", fontScale = 2f)
@Composable
private fun UpgradeHeroCardCompactPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeHeroCard(text = PREVIEW_PREAMBLE)
        }
    }
}

// Both flavors tint the hero: FOSS on primaryContainer, GPLAY on secondaryContainer. Neither is
// the composable's default, so the default-colored preview above would not catch a contrast
// regression on the colors that actually ship.
@Preview2
@Composable
private fun UpgradeHeroCardTintedPreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpgradeHeroCard(
                text = PREVIEW_PREAMBLE,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            UpgradeHeroCard(
                text = PREVIEW_PREAMBLE,
                happy = false,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
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

@Preview2
@Composable
private fun UpgradeSectionCardPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeSectionCard(
                title = "Upgrade benefits",
                icon = Icons.TwoTone.AutoAwesome,
            ) {
                UpgradeSectionBody(text = "What a section card looks like with a body and a list.")
                UpgradeFeatureList(text = "• App cache and junk cleaning\n• Duplicate file removal")
            }
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

@Preview2
@Composable
private fun UpgradeSectionHeaderPreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UpgradeSectionHeader(title = "With an icon", icon = Icons.TwoTone.AutoAwesome)
            // The `leading` slot wins over `icon` — worth seeing, since callers pass both.
            UpgradeSectionHeader(
                title = "With a leading slot",
                icon = Icons.TwoTone.AutoAwesome,
                leading = { UpgradeMascot(size = 24.dp) },
            )
        }
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

@Preview2
@Composable
private fun UpgradeSectionBodyPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeSectionBody(
                text = "Body copy for a section card, in the muted variant color the sections use.",
            )
        }
    }
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

// Covers both branches of the parser: bullet lines become checkmark rows, everything else stays
// plain text.
@Preview2
@Composable
private fun UpgradeFeatureListPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeFeatureList(
                text = """
                    A plain intro line
                    • App cache and junk cleaning
                    • Duplicate file removal
                    • More features and controls across the app
                """.trimIndent(),
            )
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

@Preview2
@Composable
private fun UpgradeFeatureRowPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeFeatureRow(text = "A single feature row")
            // Long enough to wrap: the checkmark must stay top-aligned against the first line.
            UpgradeFeatureRow(text = "A feature row long enough that it wraps onto a second line")
        }
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

@Preview2
@Composable
private fun UpgradeHintTextPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeHintText(text = "The alternative are ads, analytics and Google Play 🙁")
        }
    }
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

@Preview2
@Composable
private fun UpgradeActionCardPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeActionCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("Sponsor development")
                }
                UpgradeHintText(text = "The alternative are ads, analytics and Google Play 🙁")
            }
        }
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

// In the app this block only ever appears inside the action card, so that is how it is previewed.
@Preview2
@Composable
private fun UpgradeLoadingBlockPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeActionCard { UpgradeLoadingBlock() }
        }
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

@Preview2
@Composable
private fun UpgradeInlineStateCardPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeInlineStateCard(
                title = "Offers unavailable",
                body = "Google Play did not answer. This is usually temporary.",
                icon = Icons.TwoTone.WarningAmber,
            ) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.38f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

// The latched state the card shows after the retry was tapped: the contrast of a DISABLED button on
// errorContainer is the part the default theming gets wrong, so it needs its own preview.
@Preview2
@Composable
private fun UpgradeInlineStateCardDisabledActionPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.padding(16.dp)) {
            UpgradeInlineStateCard(
                title = "Offers unavailable",
                body = "Google Play did not answer. This is usually temporary.",
                icon = Icons.TwoTone.WarningAmber,
            ) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.38f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.1f)),
                ) {
                    Text("Retry")
                }
            }
        }
    }
}
