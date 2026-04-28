package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.compose.SdmMascot
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.upgrade.UpgradeRepo

@StringRes
private fun getRngSlogan() = when ((0..8).random()) {
    0 -> CommonR.string.slogan_message_0
    1 -> CommonR.string.slogan_message_1
    2 -> CommonR.string.slogan_message_2
    3 -> CommonR.string.slogan_message_3
    4 -> CommonR.string.slogan_message_4
    5 -> CommonR.string.slogan_message_5
    6 -> CommonR.string.slogan_message_6
    7 -> CommonR.string.slogan_message_7
    8 -> CommonR.string.slogan_message_8
    else -> throw IllegalArgumentException()
}

data class TitleDashboardCardItem(
    val upgradeInfo: UpgradeRepo.Info?,
    val isWorking: Boolean,
    val onRibbonClicked: () -> Unit,
    val webpageTool: WebpageTool,
    val onMascotTriggered: (Boolean) -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TitleDashboardCard(item: TitleDashboardCardItem) {
    val slogan = remember { getRngSlogan() }
    val sloganText = stringResource(slogan)
    val titleText = if (item.upgradeInfo?.isPro == true) {
        buildAnnotatedString {
            append(stringResource(CommonR.string.app_name))
            append(" ")
            pushStyle(SpanStyle(color = colorResource(R.color.colorUpgraded)))
            append(stringResource(R.string.app_name_upgrade_postfix))
            pop()
        }
    } else {
        buildAnnotatedString {
            append(stringResource(CommonR.string.app_name))
        }
    }

    val mascotRotation = remember { Animatable(0f) }
    val clickCount = remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        TitleHeaderLayout(
            modifier = Modifier.fillMaxWidth(),
            mascot = {
                SdmMascot(
                    modifier = Modifier
                        .height(96.dp)
                        .rotate(mascotRotation.value)
                        .pointerInput(item) {
                            detectTapGestures(
                                onTap = {
                                    val count = clickCount.intValue + 1
                                    clickCount.intValue = count
                                    scope.launch {
                                        when {
                                            count % 12 == 0 -> {
                                                mascotRotation.snapTo(0f)
                                                mascotRotation.animateTo(360f, tween(800))
                                                mascotRotation.snapTo(0f)
                                            }

                                            count % 5 == 0 -> {
                                                repeat(4) { i ->
                                                    val target = if (i % 2 == 0) 5f else -5f
                                                    mascotRotation.animateTo(target, tween(100))
                                                }
                                                mascotRotation.animateTo(0f, tween(100))
                                            }
                                        }
                                    }
                                },
                                onLongPress = {
                                    item.onMascotTriggered(true)
                                },
                            )
                        }
                        .pointerInput(item) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val longPress = awaitLongPressOrCancellation(down.id)
                                if (longPress != null) {
                                    waitForUpOrCancellation()
                                    item.onMascotTriggered(false)
                                }
                            }
                        },
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = sloganText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = if (slogan == CommonR.string.slogan_message_8) {
                            Modifier.pointerInput(item.webpageTool) {
                                detectTapGestures(
                                    onLongPress = {
                                        item.webpageTool.open("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                }
            },
            ribbon = if (BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE) {
                {
                    BuildTypeRibbon(onClick = item.onRibbonClicked)
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun TitleHeaderLayout(
    modifier: Modifier = Modifier,
    mascot: @Composable () -> Unit,
    title: @Composable () -> Unit,
    ribbon: (@Composable () -> Unit)? = null,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(contentAlignment = Alignment.Center) { mascot() }
            Box(contentAlignment = Alignment.Center) { title() }
            if (ribbon != null) {
                Box(contentAlignment = Alignment.Center) { ribbon() }
            }
        },
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val mascotPlaceable = measurables[0].measure(looseConstraints)
        val titlePlaceable = measurables[1].measure(looseConstraints)
        val ribbonPlaceable = measurables.getOrNull(2)?.measure(looseConstraints)

        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            listOfNotNull(
                mascotPlaceable.width,
                titlePlaceable.width,
                ribbonPlaceable?.width,
            ).maxOrNull() ?: 0
        }
        val inlineSpacing = 12.dp.roundToPx()
        val stackSpacing = 8.dp.roundToPx()

        val titleX = ((width - titlePlaceable.width) / 2).coerceAtLeast(0)
        val titleRight = titleX + titlePlaceable.width
        val mascotX = (titleX - inlineSpacing - mascotPlaceable.width).coerceAtLeast(0)
        val desiredRibbonX = titleRight + inlineSpacing
        val canPlaceRibbonInline = ribbonPlaceable?.let { desiredRibbonX + it.width <= width } ?: true

        val topRowHeight = maxOf(
            titlePlaceable.height,
            mascotPlaceable.height,
            if (canPlaceRibbonInline) ribbonPlaceable?.height ?: 0 else 0,
        )
        val stackedHeight = if (canPlaceRibbonInline) {
            topRowHeight
        } else {
            topRowHeight + stackSpacing + checkNotNull(ribbonPlaceable).height
        }
        val layoutHeight = if (constraints.hasBoundedHeight) {
            stackedHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        } else {
            stackedHeight.coerceAtLeast(constraints.minHeight)
        }

        layout(width, layoutHeight) {
            val topRowY = 0
            val titleY = topRowY + ((topRowHeight - titlePlaceable.height) / 2)
            val mascotY = topRowY + ((topRowHeight - mascotPlaceable.height) / 2)

            titlePlaceable.placeRelative(titleX, titleY)
            mascotPlaceable.placeRelative(mascotX, mascotY)

            ribbonPlaceable?.let { placeable ->
                if (canPlaceRibbonInline) {
                    val ribbonY = topRowY + ((topRowHeight - placeable.height) / 2)
                    placeable.placeRelative(desiredRibbonX, ribbonY)
                } else {
                    val ribbonX = ((width - placeable.width) / 2).coerceAtLeast(0)
                    val ribbonY = topRowHeight + stackSpacing
                    placeable.placeRelative(ribbonX, ribbonY)
                }
            }
        }
    }
}

@Composable
private fun BuildTypeRibbon(
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
                Box(modifier = Modifier.padding(start = 2.dp)) {
                    Text(
                        text = when (BuildConfigWrap.BUILD_TYPE) {
                            BuildConfigWrap.BuildType.DEV -> "Dev"
                            BuildConfigWrap.BuildType.BETA -> "Beta"
                            BuildConfigWrap.BuildType.RELEASE -> ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = BuildConfigWrap.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Preview2
@Composable
private fun TitleDashboardCardPreview() {
    val context = LocalContext.current
    PreviewWrapper {
        TitleDashboardCard(
            item = TitleDashboardCardItem(
                upgradeInfo = null,
                isWorking = false,
                onRibbonClicked = {},
                webpageTool = WebpageTool(context),
                onMascotTriggered = {},
            ),
        )
    }
}
