package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.sdmse.swiper.R

@Composable
internal fun SwiperGestureOverlay(
    swapDirections: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DirectionLabel(
                text = stringResource(R.string.swiper_gesture_overlay_up_skip),
                modifier = Modifier.fillMaxWidth(),
                align = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DirectionLabel(
                    text = if (swapDirections) {
                        stringResource(R.string.swiper_gesture_overlay_left_keep)
                    } else {
                        stringResource(R.string.swiper_gesture_overlay_left_delete)
                    },
                    align = TextAlign.Start,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.swiper_gesture_overlay_dismiss),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.swiper_gesture_overlay_dismiss_action))
                    }
                }
                DirectionLabel(
                    text = if (swapDirections) {
                        stringResource(R.string.swiper_gesture_overlay_right_delete)
                    } else {
                        stringResource(R.string.swiper_gesture_overlay_right_keep)
                    },
                    align = TextAlign.End,
                )
            }
            DirectionLabel(
                text = stringResource(R.string.swiper_gesture_overlay_down_undo),
                modifier = Modifier.fillMaxWidth(),
                align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DirectionLabel(
    text: String,
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        textAlign = align,
        modifier = modifier,
    )
}
