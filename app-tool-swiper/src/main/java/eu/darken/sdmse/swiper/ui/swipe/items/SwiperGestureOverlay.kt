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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
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
            .background(Color.Black.copy(alpha = 0.8f))
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
                painter = painterResource(R.drawable.ic_baseline_skip_next_24),
                text = stringResource(R.string.swiper_gesture_overlay_up_skip),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DirectionLabel(
                    imageVector = if (swapDirections) Icons.TwoTone.Favorite else Icons.TwoTone.Delete,
                    text = if (swapDirections) {
                        stringResource(R.string.swiper_gesture_overlay_left_keep)
                    } else {
                        stringResource(R.string.swiper_gesture_overlay_left_delete)
                    },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.swiper_gesture_overlay_dismiss),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = onDismiss) {
                        Text(stringResource(R.string.swiper_gesture_overlay_dismiss_action))
                    }
                }
                DirectionLabel(
                    imageVector = if (swapDirections) Icons.TwoTone.Delete else Icons.TwoTone.Favorite,
                    text = if (swapDirections) {
                        stringResource(R.string.swiper_gesture_overlay_right_delete)
                    } else {
                        stringResource(R.string.swiper_gesture_overlay_right_keep)
                    },
                )
            }
            DirectionLabel(
                imageVector = Icons.TwoTone.Restore,
                text = stringResource(R.string.swiper_gesture_overlay_down_undo),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DirectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            imageVector != null -> Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
            painter != null -> Icon(
                painter = painter,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}
