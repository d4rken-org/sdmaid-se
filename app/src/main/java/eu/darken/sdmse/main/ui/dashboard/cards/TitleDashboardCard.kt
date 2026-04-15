package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.R as UiR
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Mascot(
                modifier = Modifier
                    .height(96.dp)
                    .pointerInput(item) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPress = awaitLongPressOrCancellation(down.id)
                            if (longPress != null) {
                                item.onMascotTriggered(true)
                                waitForUpOrCancellation()
                                item.onMascotTriggered(false)
                            }
                        }
                    },
            )

            Spacer(modifier = Modifier.width(12.dp))

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

            if (BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE) {
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable(onClick = item.onRibbonClicked),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_bug_report),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
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
                        Text(
                            text = BuildConfigWrap.VERSION_NAME,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
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
