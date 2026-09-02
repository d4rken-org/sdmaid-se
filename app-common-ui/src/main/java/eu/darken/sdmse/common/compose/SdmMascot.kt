package eu.darken.sdmse.common.compose

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import eu.darken.sdmse.common.ui.R
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface SdmMascotMode {
    data object Animated : SdmMascotMode
    data object Christmas : SdmMascotMode
    data object NewYear : SdmMascotMode
    data object Party : SdmMascotMode
}

private val MASCOT_ANIMATION = LottieCompositionSpec.Asset("lottie/mascot_animation_coffee_relaxed.json")

private const val MASCOT_ASPECT_RATIO = 640f / 866f

// The Lottie composition is cropped to the character, so a hat's top sits above the box.
private val NEW_YEAR_HAT = HatConfig(
    drawableRes = R.drawable.mascot_hat_newyears_crop,
    rotation = 30f,
    widthPercent = 0.6701f,
    heightPercent = 0.4815f,
    leftPercent = 0.448f,
    topPercent = -0.195f,
    horizontalOffset = 2.dp,
    verticalOffset = (-4).dp,
)

private val CHRISTMAS_HAT = HatConfig(
    drawableRes = R.drawable.mascot_hat_xmas_crop,
    rotation = 31f,
    widthPercent = 0.6412f,
    heightPercent = 0.4739f,
    leftPercent = 0.4294f,
    topPercent = -0.1072f,
    horizontalOffset = 2.dp,
    verticalOffset = (-4).dp,
)

@Composable
fun SdmMascot(
    modifier: Modifier = Modifier,
    mode: SdmMascotMode = SdmMascotMode.Animated,
) {
    val composition = if (LocalInspectionMode.current) {
        // Previews render a single frame and never see an async load complete.
        val context = LocalContext.current
        remember { loadCompositionForPreview(context) }
    } else {
        rememberLottieComposition(MASCOT_ANIMATION).value
    }

    SdmMascotContent(
        modifier = modifier,
        composition = composition,
        mode = mode,
    )
}

@Composable
private fun SdmMascotContent(
    modifier: Modifier = Modifier,
    composition: LottieComposition?,
    mode: SdmMascotMode,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        MascotContentBox {
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.mascot_coffee_relaxed_still),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val hat = resolveHat(mode)
            if (hat != null) {
                HatOverlay(
                    hatRes = hat.drawableRes,
                    rotation = hat.rotation,
                    widthPercent = hat.widthPercent,
                    heightPercent = hat.heightPercent,
                    leftPercent = hat.leftPercent,
                    topPercent = hat.topPercent,
                    horizontalOffset = hat.horizontalOffset,
                    verticalOffset = hat.verticalOffset,
                )
            }
        }
    }
}

// Lottie's async loader decodes the embedded base64 images itself; the sync parser leaves them to
// an asset manager that previews don't have, so decode them here.
private fun loadCompositionForPreview(context: Context): LottieComposition? {
    val composition = LottieCompositionFactory.fromAssetSync(context, MASCOT_ANIMATION.assetName).value
    composition?.images?.values?.forEach { asset ->
        if (asset.bitmap != null || !asset.fileName.startsWith("data:")) return@forEach
        val data = asset.fileName.substringAfter("base64,", missingDelimiterValue = "")
        if (data.isEmpty()) return@forEach
        val bytes = try {
            Base64.decode(data, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return@forEach
        }
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inDensity = 160
        }
        asset.bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
    return composition
}

@Composable
private fun MascotContentBox(
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
    ) {
        val contentModifier = if (maxHeight > 0.dp && (maxWidth / maxHeight) > MASCOT_ASPECT_RATIO) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(MASCOT_ASPECT_RATIO, matchHeightConstraintsFirst = true)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(MASCOT_ASPECT_RATIO)
        }

        Box(modifier = contentModifier) {
            content()
        }
    }
}

private fun resolveHat(mode: SdmMascotMode): HatConfig? {
    return when (mode) {
        SdmMascotMode.Party,
        SdmMascotMode.NewYear,
        -> NEW_YEAR_HAT

        SdmMascotMode.Christmas -> CHRISTMAS_HAT

        SdmMascotMode.Animated -> {
            val now = LocalDate.now()
            when {
                isNewYears(now) -> NEW_YEAR_HAT
                isXmasSeason(now) -> CHRISTMAS_HAT
                else -> null
            }
        }
    }
}

private data class HatConfig(
    val drawableRes: Int,
    val rotation: Float,
    val widthPercent: Float,
    val heightPercent: Float,
    val leftPercent: Float,
    val topPercent: Float,
    val horizontalOffset: Dp = 0.dp,
    val verticalOffset: Dp = 0.dp,
)

private fun isXmasSeason(now: LocalDate): Boolean {
    val start = LocalDate.of(now.year, Month.DECEMBER, 21)
    val end = LocalDate.of(now.year, Month.DECEMBER, 29)
    return now.isEqual(start) || now.isEqual(end) || (now.isAfter(start) && now.isBefore(end))
}

private fun isNewYears(now: LocalDate): Boolean {
    val newYearsEveThisYear = LocalDate.of(now.year, 12, 31)
    val newYearsEveLastYear = LocalDate.of(now.year - 1, 12, 31)
    val daysDifferenceThisYear = abs(ChronoUnit.DAYS.between(now, newYearsEveThisYear))
    val daysDifferenceLastYear = abs(ChronoUnit.DAYS.between(now, newYearsEveLastYear))
    return daysDifferenceThisYear <= 2 || daysDifferenceLastYear <= 2
}

@Preview2
@Composable
private fun SdmMascotPreview() {
    SdmMascotPreviewContent()
}

@Preview2
@Composable
private fun SdmMascotChristmasPreview() {
    SdmMascotPreviewContent(mode = SdmMascotMode.Christmas)
}

@Preview2
@Composable
private fun SdmMascotNewYearPreview() {
    SdmMascotPreviewContent(mode = SdmMascotMode.NewYear)
}

@Preview2
@Composable
private fun SdmMascotPartyPreview() {
    SdmMascotPreviewContent(mode = SdmMascotMode.Party)
}

@Preview2
@Composable
private fun SdmMascotFallbackPreview() {
    PreviewWrapper {
        SdmMascotContent(
            modifier = Modifier.size(172.dp),
            composition = null,
            mode = SdmMascotMode.Party,
        )
    }
}

@Composable
private fun SdmMascotPreviewContent(
    mode: SdmMascotMode = SdmMascotMode.Animated,
) {
    PreviewWrapper {
        SdmMascot(
            modifier = Modifier.size(172.dp),
            mode = mode,
        )
    }
}

@Composable
private fun HatOverlay(
    hatRes: Int,
    rotation: Float,
    widthPercent: Float,
    heightPercent: Float,
    leftPercent: Float,
    topPercent: Float,
    horizontalOffset: Dp,
    verticalOffset: Dp,
) {
    Layout(
        content = {
            Image(
                painter = painterResource(hatRes),
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val hatWidth = (constraints.maxWidth * widthPercent).roundToInt()
        val hatHeight = (constraints.maxHeight * heightPercent).roundToInt()

        val hatConstraints = constraints.copy(
            minWidth = hatWidth,
            maxWidth = hatWidth,
            minHeight = hatHeight,
            maxHeight = hatHeight,
        )
        val placeable = measurables.first().measure(hatConstraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (constraints.maxWidth * leftPercent).roundToInt() + horizontalOffset.roundToPx()
            val y = (constraints.maxHeight * topPercent).roundToInt() + verticalOffset.roundToPx()
            placeable.place(x, y)
        }
    }
}
