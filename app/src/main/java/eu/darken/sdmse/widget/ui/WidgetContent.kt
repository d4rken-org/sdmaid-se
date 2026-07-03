package eu.darken.sdmse.widget.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import eu.darken.sdmse.R
import eu.darken.sdmse.main.core.shortcuts.AppShortcut
import eu.darken.sdmse.main.ui.MainActivity
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity
import eu.darken.sdmse.widget.WidgetRenderState
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.ui.R as CommonUiR

// Breakpoints on the actual cell size (SizeMode.Exact). Column↔dp is approximate.
private val STACKED_MIN_HEIGHT = 110.dp
private val RING_MAX_WIDTH = 190.dp     // below this (1 row) → ring (~2 col)
private val BUTTON_LABEL_MIN_WIDTH = 300.dp // at/above this (1 row) → show the Clean text (~4 col)

// Shared with WidgetChrome's padding() call below so the two stay in sync.
private val WIDGET_CHROME_VERTICAL_PADDING = 12.dp

// WidgetChrome's vertical padding (both sides) leaves ~56dp of content height at 80dp outer height —
// enough for the row's text+bar+freed-text stack; below this the compact 2-line layout (value + bar)
// stays as-is. Must stay below STACKED_MIN_HEIGHT, otherwise ValueRowLayout is never reached at this
// height and the freed-text branch becomes dead code.
private val VALUE_ROW_FREED_MIN_HEIGHT = 80.dp

// Equal element size for the symmetric narrow (ring) row.
private val NARROW_ELEMENT_SIZE = 44.dp

// NOTE: the widget root must NOT be clickable. A clickable parent swallows taps on the clickable
// Clean button nested inside it, so the button would open the app instead of cleaning. Instead the
// "open app" click sits on a content group that is a *sibling* of the Clean button.
//
// Each Action's Intent must be `filterEquals`-distinct, otherwise their PendingIntents collapse into
// one. `Intent.filterEquals` compares component/action/data but IGNORES extras and flags, so the two
// MainActivity intents below (which differ only by extra + flags) would otherwise share a single
// PendingIntent and route every tap to whichever was registered — that made storage taps open the
// dashboard instead of the Analyzer. Distinct `data` URIs keep them separate. `clean()` already
// differs by component (ShortcutActivity) + action.
private val URI_OPEN_APP = Uri.parse("sdmse://widget/home")
private val URI_OPEN_ANALYZER = Uri.parse("sdmse://widget/analyzer")

// Extracted + internal so the filterEquals-distinctness that keeps their PendingIntents from
// collapsing (and `clean()`'s) is unit-testable without a composition. See WidgetIntentsTest.
internal fun widgetOpenAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        data = URI_OPEN_APP
        // Behave like a launcher tap: reuse a live MainActivity instead of stacking a second one.
        // The data URI (needed for PendingIntent identity) stops the system's own root-intent
        // matching, so without these flags a backgrounded app would get a duplicate dashboard
        // instance on top — and backing out of that left the singleton NavigationController wired
        // to the finished instance's back stack, freezing all navigation (device-confirmed).
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

internal fun widgetOpenAnalyzerIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        data = URI_OPEN_ANALYZER
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION, ShortcutActivity.ACTION_OPEN_ANALYZER)
    }

internal fun widgetCancelIntent(context: Context): Intent =
    Intent(context, ShortcutActivity::class.java).apply {
        action = ShortcutActivity.ACTION_CANCEL_ONECLICK
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

@Composable
private fun openApp(): Action = actionStartActivity(widgetOpenAppIntent(LocalContext.current))

@Composable
private fun openAnalyzer(): Action = actionStartActivity(widgetOpenAnalyzerIntent(LocalContext.current))

@Composable
private fun cancel(): Action = actionStartActivity(widgetCancelIntent(LocalContext.current))

@Composable
private fun clean(): Action = actionStartActivity(AppShortcut.MainAction.OneTap.createIntent(LocalContext.current))

@Composable
internal fun WidgetContent(state: WidgetRenderState) {
    WidgetChrome {
        when (state) {
            is WidgetRenderState.Data -> {
                val size = LocalSize.current
                when {
                    size.height >= STACKED_MIN_HEIGHT -> StackedLayout(state)
                    size.width < RING_MAX_WIDTH -> RingRowLayout(state)
                    else -> ValueRowLayout(
                        state,
                        showButtonLabel = size.width >= BUTTON_LABEL_MIN_WIDTH,
                        showFreedText = size.height >= VALUE_ROW_FREED_MIN_HEIGHT,
                    )
                }
            }

            WidgetRenderState.Unavailable -> UnavailableContent()
        }
    }
}

/**
 * Widget-picker preview content: always the stacked (default 3×2) layout. The preview surface has no
 * real host size — Glance composes it at the provider's minimum resize size, which the LocalSize
 * dispatch in [WidgetContent] would resolve to the narrow ring row; the stacked layout is the
 * representative showcase.
 */
@Composable
internal fun WidgetPreviewContent(state: WidgetRenderState.Data) {
    WidgetChrome {
        StackedLayout(state)
    }
}

@Composable
private fun WidgetChrome(content: @Composable () -> Unit) {
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.background)
                .cornerRadius(20.dp)
                .padding(horizontal = 14.dp, vertical = WIDGET_CHROME_VERTICAL_PADDING),
        ) {
            content()
        }
    }
}

/** Tall (2+ rows): branding + storage (tap → app) at the top, Clean button pinned to the bottom. */
@Composable
private fun StackedLayout(data: WidgetRenderState.Data) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        BrandingHeader(data.freedBytes, GlanceModifier.fillMaxWidth().clickable(openApp()))
        Spacer(GlanceModifier.height(12.dp))
        Column(modifier = GlanceModifier.fillMaxWidth().clickable(openAnalyzer())) {
            data.storages.forEachIndexed { index, entry ->
                if (index > 0) Spacer(GlanceModifier.height(10.dp))
                StorageRow(entry)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.height(12.dp))
        CleanButton(GlanceModifier.fillMaxWidth(), mode = data.cleanMode)
    }
}

/**
 * 1 row, medium/wide: mascot + primary storage (tap → app) + Clean button. The button label only
 * shows at the widest sizes; at ~3 columns it's icon-only so the value isn't truncated.
 */
@Composable
private fun ValueRowLayout(data: WidgetRenderState.Data, showButtonLabel: Boolean, showFreedText: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mascot(40.dp, GlanceModifier.clickable(openApp()))
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight().clickable(openAnalyzer())) {
            data.storages.firstOrNull()?.let { entry ->
                Text(
                    text = usedOfTotal(context, entry),
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(3.dp))
                StorageBar(entry.usedRatio)
                if (showFreedText) {
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        text = freedLabel(context, data.freedBytes),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(GlanceModifier.width(12.dp))
        CleanButton(showLabel = showButtonLabel, mode = data.cleanMode)
    }
}

/**
 * 1 row, narrow (~2 col): mascot, storage ring and Clean button as three equal-size circles, evenly
 * distributed. Mascot + ring tap → app; the button cleans.
 */
@Composable
private fun RingRowLayout(data: WidgetRenderState.Data) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mascot(NARROW_ELEMENT_SIZE, GlanceModifier.clickable(openApp()))
        Spacer(GlanceModifier.defaultWeight())
        Box(modifier = GlanceModifier.size(NARROW_ELEMENT_SIZE).clickable(openAnalyzer())) {
            data.storages.firstOrNull()?.let { StorageRing(it.usedRatio, NARROW_ELEMENT_SIZE) }
        }
        Spacer(GlanceModifier.defaultWeight())
        CleanCircle(NARROW_ELEMENT_SIZE, mode = data.cleanMode)
    }
}

@Composable
private fun BrandingHeader(freedBytes: Long, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Mascot(42.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(R.string.widget_home_title),
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = freedLabel(context, freedBytes),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Mascot(size: Dp, modifier: GlanceModifier = GlanceModifier) {
    Image(
        provider = ImageProvider(R.mipmap.ic_launcher_round),
        contentDescription = null,
        modifier = GlanceModifier.size(size).then(modifier),
    )
}

@Composable
private fun StorageRow(entry: WidgetRenderState.Data.StorageEntry) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(storageLabelRes(entry.kind)),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            // Weighted spacer keeps the label at its natural width and pushes the value to the right
            // edge (weighting the label Text itself collapses it to zero width in Glance).
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = usedOfTotal(context, entry),
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(5.dp))
        StorageBar(entry.usedRatio)
    }
}

@Composable
private fun StorageBar(ratio: Float) {
    LinearProgressIndicator(
        progress = ratio,
        modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.secondaryContainer,
    )
}

/**
 * The Clean control's three faces. While work runs it's a muted, non-primary affordance; the widget
 * re-renders on busy/cancellable transitions (WidgetRefreshCoordinator), so the action swaps with the
 * visual: CANCEL stops everything, WORKING (mid-cancel) just opens the app to watch.
 */
private enum class CleanMode { CLEAN, CANCEL, WORKING }

private val WidgetRenderState.Data.cleanMode: CleanMode
    get() = when {
        isWorking && isCancellable -> CleanMode.CANCEL
        isWorking -> CleanMode.WORKING
        else -> CleanMode.CLEAN
    }

@Composable
private fun CleanMode.action(): Action = when (this) {
    CleanMode.CLEAN -> clean()
    CleanMode.CANCEL -> cancel()
    CleanMode.WORKING -> openApp()
}

private fun CleanMode.label(context: Context): String = when (this) {
    CleanMode.CLEAN -> context.getString(R.string.widget_home_clean_action)
    CleanMode.CANCEL -> context.getString(CommonR.string.general_cancel_action)
    CleanMode.WORKING -> context.getString(R.string.widget_home_working)
}

@Composable
private fun CleanButton(modifier: GlanceModifier = GlanceModifier, showLabel: Boolean = true, mode: CleanMode = CleanMode.CLEAN) {
    val context = LocalContext.current
    val bg = if (mode == CleanMode.CLEAN) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val fg = if (mode == CleanMode.CLEAN) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    val label = mode.label(context)
    Row(
        modifier = modifier
            .background(bg)
            .cornerRadius(22.dp)
            .clickable(mode.action())
            .padding(horizontal = if (showLabel) 16.dp else 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (mode) {
            CleanMode.WORKING -> CircularProgressIndicator(modifier = GlanceModifier.size(18.dp), color = fg)
            else -> Image(
                provider = ImageProvider(
                    if (mode == CleanMode.CANCEL) CommonUiR.drawable.ic_cancel
                    else CommonUiR.drawable.ic_baseline_delete_sweep_24
                ),
                contentDescription = label,
                colorFilter = ColorFilter.tint(fg),
                modifier = GlanceModifier.size(18.dp),
            )
        }
        if (showLabel) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = label,
                style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/** Circular icon-only Clean button, sized to match the mascot and ring in the narrow layout. */
@Composable
private fun CleanCircle(size: Dp, mode: CleanMode = CleanMode.CLEAN) {
    val context = LocalContext.current
    val bg = if (mode == CleanMode.CLEAN) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val fg = if (mode == CleanMode.CLEAN) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(bg)
            .cornerRadius(size / 2)
            .clickable(mode.action()),
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            CleanMode.WORKING -> CircularProgressIndicator(modifier = GlanceModifier.size(size / 2), color = fg)
            else -> Image(
                provider = ImageProvider(
                    if (mode == CleanMode.CANCEL) CommonUiR.drawable.ic_cancel
                    else CommonUiR.drawable.ic_baseline_delete_sweep_24
                ),
                contentDescription = mode.label(context),
                colorFilter = ColorFilter.tint(fg),
                modifier = GlanceModifier.size(size / 2),
            )
        }
    }
}

@Composable
private fun UnavailableContent() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(openApp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(28.dp)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_home_unavailable),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

private fun usedOfTotal(context: Context, entry: WidgetRenderState.Data.StorageEntry): String =
    "${formatSize(context, entry.usedBytes)} / ${formatSize(context, entry.totalBytes)}"

private fun storageLabelRes(kind: WidgetRenderState.Data.StorageEntry.Kind): Int = when (kind) {
    WidgetRenderState.Data.StorageEntry.Kind.INTERNAL -> R.string.widget_home_storage_internal
    WidgetRenderState.Data.StorageEntry.Kind.EXTERNAL -> R.string.widget_home_storage_external
}

private fun formatSize(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes)

private fun freedLabel(context: Context, freedBytes: Long): String =
    context.getString(R.string.widget_home_freed_label, formatSize(context, freedBytes))
