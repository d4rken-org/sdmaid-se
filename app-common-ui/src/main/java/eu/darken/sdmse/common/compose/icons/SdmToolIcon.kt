package eu.darken.sdmse.common.compose.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.main.core.SDMTool

val SDMTool.Type.icon: ImageVector
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> SdmIcons.Ghost
        SDMTool.Type.SYSTEMCLEANER -> Icons.AutoMirrored.Outlined.ViewList
        SDMTool.Type.APPCLEANER -> Icons.Outlined.Recycling
        SDMTool.Type.DEDUPLICATOR -> Icons.Outlined.ContentCopy
        SDMTool.Type.SQUEEZER -> Icons.Outlined.Compress
        SDMTool.Type.APPCONTROL -> Icons.Outlined.Apps
        SDMTool.Type.ANALYZER -> Icons.Outlined.DataUsage
        SDMTool.Type.SWIPER -> Icons.Outlined.Swipe
    }
