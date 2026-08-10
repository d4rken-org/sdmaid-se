package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.sieve.SieveCriterium
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

/** The leading mode target. Deliberately not an IconButton: that applies
 * `minimumInteractiveComponentSize()` (48dp) and the overhang eats the chip body on short labels. */
private val MODE_TARGET_SIZE = 32.dp

/**
 * Reference string for the label's minimum width. A one-character entry otherwise renders as a
 * sliver between the two icons and reads as broken next to its neighbours.
 *
 * Measured rather than hardcoded as dp so it tracks the system font scale and the locale's actual
 * glyphs. Digits are the reference because they are visually uniform — a letter-based one would
 * swing wildly between "iii" and "WWW" and the minimum would mean something different per locale.
 */
private const val MIN_LABEL_REFERENCE = "000"

@Composable
internal fun TaggedChip(
    modifier: Modifier = Modifier,
    criterium: SieveCriterium,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onShowModeSwitcher: () -> Unit,
) {
    // Display form: a whitespace-only value would otherwise be a blank chip, and every label built
    // from it ("Edit  ") would be read out as a dangling fragment.
    val label = criteriumDisplayText(criteriumValue(criterium))
    val editLabel = stringResource(CommonR.string.general_edit_x_action, label)
    val modeLabel = stringResource(
        SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_mode_x_action,
        label,
        stringResource(criteriumModeLabelRes(criterium)),
    )
    InputChip(
        selected = false,
        // The gesture has to be the chip's own onClick: InputChip renders a Surface that appends its
        // own selectable AFTER whatever modifier we pass in. That inner node sees the bubbling pointer
        // pass first and consumes the down, so an outer clickable/combinedClickable never fires.
        onClick = onEdit,
        // SelectableChip advertises Role.Checkbox, which is wrong now that a tap opens the editor.
        // Applied before the chip's own semantics so these outermost values win (collapsePeer keeps
        // the outermost value for a plain key, and the outermost non-null label for an action key).
        modifier = modifier.semantics {
            role = Role.Button
            onClick(label = editLabel, action = null)
        },
        label = {
            // Inside the label slot LocalTextStyle is the chip's own labelTextStyle, so the
            // reference is measured with exactly the style the label below renders with.
            val measurer = rememberTextMeasurer()
            val labelStyle = LocalTextStyle.current
            val density = LocalDensity.current
            val minLabelWidth = remember(measurer, labelStyle, density) {
                with(density) { measurer.measure(MIN_LABEL_REFERENCE, labelStyle).size.width.toDp() }
            }
            Text(
                text = label,
                modifier = Modifier.widthIn(min = minLabelWidth),
                // Only bites below the minimum, where it centres the label instead of leaving it
                // hugging the leading edge with dead space trailing it.
                textAlign = TextAlign.Center,
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(MODE_TARGET_SIZE)
                    .clickable(role = Role.Button, onClick = onShowModeSwitcher),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = criteriumIcon(criterium),
                    contentDescription = modeLabel,
                    modifier = Modifier.size(InputChipDefaults.IconSize),
                )
            }
        },
        trailingIcon = {
            // Pinned to 24dp: without it the button expands to LocalMinimumInteractiveComponentSize
            // (40dp here) and drags the whole chip taller than the neighbouring Add chip. The size
            // only constrains layout — the touch target still overhangs the chip bounds (measured
            // 44x48dp on device), so reachability is unaffected.
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    // Not the "Remove %s filter" wording: these are the filter's entries, and
                    // announcing one as a filter contradicts the rest of the screen.
                    contentDescription = stringResource(CommonR.string.general_remove_x_action, label),
                    modifier = Modifier.size(InputChipDefaults.IconSize),
                )
            }
        },
    )
}
