package eu.darken.sdmse.common.compose

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Lets descendants temporarily suppress the focus ring — e.g. the guided tour hides it while
 * D-pad focus is still in the scrimmed background (pending-target window), where a bright ring
 * above the scrim would highlight a control the user is deliberately shielded from.
 */
@Stable
class FocusHighlightController {
    var suppressed by mutableStateOf(false)
}

val LocalFocusHighlightController = staticCompositionLocalOf<FocusHighlightController?> { null }

/**
 * Whether this device has the kind of input the focus ring exists for: a D-pad/remote, a hardware
 * keyboard, or a TV.
 *
 * `InputMode.Keyboard` alone is NOT enough to conclude the user is on a remote. Compose mirrors the
 * platform's touch-mode flag, which any navigation key event clears *display-wide* — and SD Maid's
 * own AppCleaner injects DPAD_DOWN/RIGHT/CENTER to clear caches on Android 16+ Pixels, where
 * Settings hides the "Clear cache" button from the accessibility service. The app therefore knocked
 * itself out of touch mode and drew a remote-control ring for a touch-only phone user, which stayed
 * until the next screen tap (support report: blue box around the cancel button, Pixel 8 Pro / A17).
 *
 * [Configuration] cannot be forged by that automation: WindowManager computes it
 * (DisplayContent.computeScreenConfiguration) by walking the real input devices while explicitly
 * skipping `device.isVirtual()`, and both `input keyevent` and `performGlobalAction` originate from
 * virtual devices. Attaching or removing a keyboard/remote is a configuration change, so this
 * re-evaluates on its own.
 *
 * Known limitation: this proves the hardware is *present*, not that a real keypress moved focus. On
 * a phone with a keyboard already paired, AppCleaner's injected D-pad can still raise the ring.
 * Closing that would need per-window key-event tracking.
 *
 * TV coverage needs all the signals. Standard Android TV/Google TV builds report
 * [Configuration.NAVIGATION_DPAD] even with the remote disconnected, but only because the canonical
 * TV overlay sets `config_hasPermanentDpad` — an overlay resource, not an API guarantee. A
 * third-party Leanback box may set neither it nor [Configuration.UI_MODE_TYPE_TELEVISION], which is
 * why [isTvLike] is a floor. Do not drop any of them.
 *
 * Trackball and wheel are deliberately excluded: the hardware is extinct, and AOSP classifies a
 * device offering both trackball and D-pad sources as trackball, so accepting it would widen the
 * gate for no living device.
 */
internal fun supportsFocusHighlightInput(
    navigation: Int,
    keyboard: Int,
    uiMode: Int,
    isTvLike: Boolean,
): Boolean = isTvLike ||
    navigation == Configuration.NAVIGATION_DPAD ||
    keyboard == Configuration.KEYBOARD_QWERTY ||
    (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

@Composable
private fun supportsFocusHighlightInput(): Boolean {
    val config = LocalConfiguration.current
    val context = LocalContext.current
    // Fixed for the process lifetime, unlike the Configuration signals.
    val isTvLike = remember(context) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
    return supportsFocusHighlightInput(config.navigation, config.keyboard, config.uiMode, isTvLike)
}

/**
 * Draws a high-contrast ring around whichever descendant of this node currently holds focus, but
 * only while the input mode is [InputMode.Keyboard] AND the device actually has the hardware the
 * ring exists for (see [supportsFocusHighlightInput]). Touch interaction never shows the ring — including programmatic focus
 * requests like onboarding's auto-focused Continue button — and both signals are observed
 * reactively, so the ring appears/disappears as the user switches between remote and touch.
 *
 * Drawing happens after this node's content, i.e. above all children. Because the ring is based
 * on [onFocusedBoundsChanged] (not indication), it works for every focusable — Material3
 * components included, which bypass LocalIndication and would never show a custom
 * Indication-based treatment.
 *
 * Apply to a window-root container: [FocusHighlightOverlay] does this for the main content via
 * SdmSeTheme; dialogs need their own application (separate windows) — see SdmAlertDialog.
 */
fun Modifier.focusHighlightRing(
    controller: FocusHighlightController? = null,
): Modifier = composed {
    val inputModeManager = LocalInputModeManager.current
    val supportsFocusHighlight = supportsFocusHighlightInput()
    var selfCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focusedCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The same LayoutCoordinates instance can be reported again after a reposition; bump a
    // counter so the draw pass re-reads bounds even when the state reference doesn't change.
    var positionTick by remember { mutableIntStateOf(0) }
    val ringColor = MaterialTheme.colorScheme.tertiary

    this
        .onGloballyPositioned { selfCoords = it }
        .onFocusedBoundsChanged {
            focusedCoords = it
            positionTick++
        }
        .drawWithContent {
            drawContent()

            if (inputModeManager.inputMode != InputMode.Keyboard) return@drawWithContent
            if (!supportsFocusHighlight) return@drawWithContent
            if (controller?.suppressed == true) return@drawWithContent
            @Suppress("UNUSED_EXPRESSION") positionTick
            val self = selfCoords?.takeIf { it.isAttached } ?: return@drawWithContent
            val focused = focusedCoords?.takeIf { it.isAttached } ?: return@drawWithContent
            // clipBounds=true: a focused item half-scrolled out of its container gets its
            // ring confined to the visible part instead of floating over other content.
            val bounds = self.localBoundingBoxOf(focused, clipBounds = true)
            if (bounds.isEmpty) return@drawWithContent

            val pad = RING_PADDING.toPx()
            val stroke = RING_WIDTH.toPx()
            val glow = GLOW_WIDTH.toPx()
            val topLeft = Offset(bounds.left - pad, bounds.top - pad)
            val ringSize = Size(bounds.width + 2 * pad, bounds.height + 2 * pad)
            val corner = CornerRadius(RING_CORNER_RADIUS.toPx())

            // Soft halo behind the ring for 10-foot visibility, then the crisp ring itself.
            drawRoundRect(
                color = ringColor.copy(alpha = 0.35f),
                topLeft = topLeft,
                size = ringSize,
                cornerRadius = corner,
                style = Stroke(width = glow),
            )
            drawRoundRect(
                color = ringColor,
                topLeft = topLeft,
                size = ringSize,
                cornerRadius = corner,
                style = Stroke(width = stroke),
            )
        }
}

/**
 * Mounted once in SdmSeTheme around the app content: applies [focusHighlightRing] to the main
 * window and provides the [FocusHighlightController] that lets the guided tour suppress the ring.
 */
@Composable
fun FocusHighlightOverlay(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val controller = remember { FocusHighlightController() }
    Box(modifier.focusHighlightRing(controller)) {
        CompositionLocalProvider(LocalFocusHighlightController provides controller) {
            content()
        }
    }
}

private val RING_PADDING = 2.dp
private val RING_WIDTH = 3.dp
private val GLOW_WIDTH = 8.dp
private val RING_CORNER_RADIUS = 12.dp

@Preview2
@Composable
private fun FocusHighlightOverlayPreview() {
    PreviewWrapper {
        FocusHighlightOverlay {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp, 48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {},
                ) {
                    Text(
                        text = "Focusable",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
