package eu.darken.sdmse.common.compose.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ViewList
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.DataUsage
import androidx.compose.material.icons.twotone.Recycling
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.main.core.SDMTool

val SDMTool.Type.icon: ImageVector
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> SdmIcons.Ghost
        SDMTool.Type.SYSTEMCLEANER -> Icons.AutoMirrored.TwoTone.ViewList
        SDMTool.Type.APPCLEANER -> Icons.TwoTone.Recycling
        SDMTool.Type.DEDUPLICATOR -> Icons.TwoTone.ContentCopy
        SDMTool.Type.SQUEEZER -> Icons.TwoTone.Compress
        SDMTool.Type.APPCONTROL -> Icons.TwoTone.Apps
        SDMTool.Type.ANALYZER -> Icons.TwoTone.DataUsage
        SDMTool.Type.SWIPER -> Icons.TwoTone.Swipe
    }
