package eu.darken.sdmse.common.compose

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieDynamicProperties
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.model.KeyPath
import eu.darken.sdmse.common.ui.R
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface SdmMascotMode {
    data object Animated : SdmMascotMode
    data object Halloween : SdmMascotMode
    data object Christmas : SdmMascotMode
    data object NewYear : SdmMascotMode
    data object Party : SdmMascotMode
}

internal val MASCOT_ANIMATION = LottieCompositionSpec.Asset("lottie/mascot_animation_coffee_relaxed.json")

// The Lottie composition is cropped to the character; hats sit partly above and beside it.
private const val MASCOT_ASPECT_RATIO = 640f / 866f

private val NEW_YEAR_HAT = HatConfig(
    drawableRes = R.drawable.mascot_hat_newyears,
    rotation = 25f,
    widthPercent = 0.6701f,
    heightPercent = 0.4815f,
    leftPercent = 0.4555f,
    topPercent = -0.2264f,
    visibleLeft = 0.4751f,
    visibleTop = -0.2564f,
    visibleRight = 1.162f,
    visibleBottom = 0.2782f,
)

private val CHRISTMAS_HAT = HatConfig(
    drawableRes = R.drawable.mascot_hat_xmas,
    rotation = 31f,
    widthPercent = 0.4396f,
    heightPercent = 0.2596f,
    leftPercent = 0.5599f,
    topPercent = -0.0262f,
    visibleLeft = 0.5042f,
    visibleTop = -0.0453f,
    visibleRight = 0.9745f,
    visibleBottom = 0.2642f,
)

private val HALLOWEEN_HAT = HatConfig(
    drawableRes = R.drawable.mascot_hat_witch,
    rotation = 15f,
    widthPercent = 0.57f,
    heightPercent = 0.3932f,
    leftPercent = 0.45f,
    topPercent = -0.17f,
    visibleLeft = 0.4012f,
    visibleTop = -0.1464f,
    visibleRight = 1.052f,
    visibleBottom = 0.2508f,
)

internal const val MASCOT_ROOT_LAYER = "mascot_root"
internal const val CUP_HANDLE_LAYER = "cup_handle"

/** Turns on its cup layer and recolors the handle to match; SdmMascotCostumeLayersTest pins that every cup layer starts hidden. */
internal enum class CupCostume(val layerName: String, val handleColor: Int) {
    HALLOWEEN("cup_halloween", 0xFFD35400.toInt()),
    CHRISTMAS("cup_christmas", 0xFFD62828.toInt()),
    NEW_YEAR("cup_newyear", 0xFFE0A82E.toInt()),
}

private data class MascotCostume(val hat: HatConfig, val cup: CupCostume?)

private val HALLOWEEN_COSTUME = MascotCostume(HALLOWEEN_HAT, CupCostume.HALLOWEEN)
private val CHRISTMAS_COSTUME = MascotCostume(CHRISTMAS_HAT, CupCostume.CHRISTMAS)
private val NEW_YEAR_COSTUME = MascotCostume(NEW_YEAR_HAT, CupCostume.NEW_YEAR)
private val PARTY_COSTUME = MascotCostume(NEW_YEAR_HAT, cup = null)

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

/**
 * Lays out the character box plus, when a hat is shown, the hat's visible extent, so the whole
 * drawing stays inside the reported size. In a fixed slot the character shrinks to make room.
 */
@Composable
private fun SdmMascotContent(
    modifier: Modifier = Modifier,
    composition: LottieComposition?,
    mode: SdmMascotMode,
) {
    // Store screenshots render previews; keep them free of whatever season they happen to be taken in.
    val costume = resolveCostume(mode, today = if (LocalInspectionMode.current) null else LocalDate.now())
    val hat = costume?.hat
    val dynamicProperties = remember(costume?.cup) { costume?.cup?.toDynamicProperties() }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Layout(
            content = {
                Box(modifier = Modifier.layoutId(CHARACTER_ID)) {
                    if (composition != null) {
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            dynamicProperties = dynamicProperties,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.mascot_coffee_relaxed_still),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (hat != null) {
                    Image(
                        painter = painterResource(hat.drawableRes),
                        contentDescription = null,
                        modifier = Modifier
                            .layoutId(HAT_ID)
                            .rotate(hat.rotation),
                    )
                }
            },
        ) { measurables, constraints ->
            // Frame in character-box fractions: x relative to its width, y relative to its height.
            val frameLeft = minOf(0f, hat?.visibleLeft ?: 0f)
            val frameTop = minOf(0f, hat?.visibleTop ?: 0f)
            val frameRight = maxOf(1f, hat?.visibleRight ?: 1f)
            val frameBottom = maxOf(1f, hat?.visibleBottom ?: 1f)
            val frameWidthFraction = frameRight - frameLeft
            val frameHeightFraction = frameBottom - frameTop

            val widthFromWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth / frameWidthFraction
            } else {
                Float.MAX_VALUE
            }
            val widthFromHeight = if (constraints.hasBoundedHeight) {
                constraints.maxHeight / frameHeightFraction * MASCOT_ASPECT_RATIO
            } else {
                Float.MAX_VALUE
            }
            val characterWidth = minOf(widthFromWidth, widthFromHeight).takeIf { it != Float.MAX_VALUE } ?: 0f
            val characterHeight = characterWidth / MASCOT_ASPECT_RATIO

            val character = measurables.first { it.layoutId == CHARACTER_ID }
                .measure(Constraints.fixed(characterWidth.roundToInt(), characterHeight.roundToInt()))
            val hatPlaceable = hat?.let { config ->
                measurables.first { it.layoutId == HAT_ID }.measure(
                    Constraints.fixed(
                        (characterWidth * config.widthPercent).roundToInt(),
                        (characterHeight * config.heightPercent).roundToInt(),
                    )
                )
            }

            val width = constraints.constrainWidth((characterWidth * frameWidthFraction).roundToInt())
            val height = constraints.constrainHeight((characterHeight * frameHeightFraction).roundToInt())
            layout(width, height) {
                val originX = -frameLeft * characterWidth
                val originY = -frameTop * characterHeight
                character.place(originX.roundToInt(), originY.roundToInt())
                if (hat != null && hatPlaceable != null) {
                    hatPlaceable.place(
                        (originX + hat.leftPercent * characterWidth).roundToInt(),
                        (originY + hat.topPercent * characterHeight).roundToInt(),
                    )
                }
            }
        }
    }
}

private const val CHARACTER_ID = "character"
private const val HAT_ID = "hat"

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

private fun CupCostume.toDynamicProperties() = LottieDynamicProperties(
    listOf(
        LottieDynamicProperty(LottieProperty.TRANSFORM_OPACITY, KeyPath(MASCOT_ROOT_LAYER, layerName), 100),
        LottieDynamicProperty(LottieProperty.STROKE_COLOR, KeyPath(MASCOT_ROOT_LAYER, CUP_HANDLE_LAYER, "**"), handleColor),
    )
)

private fun resolveCostume(mode: SdmMascotMode, today: LocalDate?): MascotCostume? = when (mode) {
    SdmMascotMode.Party -> PARTY_COSTUME
    SdmMascotMode.NewYear -> NEW_YEAR_COSTUME
    SdmMascotMode.Christmas -> CHRISTMAS_COSTUME
    SdmMascotMode.Halloween -> HALLOWEEN_COSTUME
    SdmMascotMode.Animated -> when (today?.let { mascotSeasonOf(it) }) {
        MascotSeason.NEW_YEAR -> NEW_YEAR_COSTUME
        MascotSeason.CHRISTMAS -> CHRISTMAS_COSTUME
        MascotSeason.HALLOWEEN -> HALLOWEEN_COSTUME
        null -> null
    }
}

internal enum class MascotSeason { HALLOWEEN, CHRISTMAS, NEW_YEAR }

internal fun mascotSeasonOf(date: LocalDate): MascotSeason? = when {
    isNewYears(date) -> MascotSeason.NEW_YEAR
    isXmasSeason(date) -> MascotSeason.CHRISTMAS
    isHalloweenSeason(date) -> MascotSeason.HALLOWEEN
    else -> null
}

/**
 * All values are fractions of the character box. The image box is the unrotated drawable, the
 * visible bounds are its opaque pixels after rotation plus a 2% margin; the frame grows to
 * include them.
 */
private data class HatConfig(
    val drawableRes: Int,
    val rotation: Float,
    val widthPercent: Float,
    val heightPercent: Float,
    val leftPercent: Float,
    val topPercent: Float,
    val visibleLeft: Float,
    val visibleTop: Float,
    val visibleRight: Float,
    val visibleBottom: Float,
)

private fun isHalloweenSeason(now: LocalDate): Boolean =
    now.month == Month.OCTOBER && now.dayOfMonth >= 24

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
private fun SdmMascotHalloweenPreview() {
    SdmMascotPreviewContent(mode = SdmMascotMode.Halloween)
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
