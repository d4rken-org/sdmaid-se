package eu.darken.sdmse.common.compose.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

// Porter's brand mark, so it keeps its own colours: render with Image, not Icon, which would
// flatten all eighteen paths to a single tint.
val SdmIcons.Porter: ImageVector
    get() {
        _porter?.let { return it }
        return ImageVector.Builder(
            name = "Porter",
            defaultWidth = 23.2.dp,
            defaultHeight = 24.dp,
            viewportWidth = 460.6f,
            viewportHeight = 476.4f,
        ).apply {
            addPath(
                pathData = addPathNodes(
                    "M231.55,144.64c5.9.2,10,.4,10,.4v-.6c-3.7,0-7.1.1-10.1.2h.1Z"
                ),
                fill = SolidColor(Color(0xFF37445A)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M221.55,144.44v.6s4-.2,10-.4c-3,0-6.4-.2-10.1-.2h.1Z"
                ),
                fill = SolidColor(Color(0xFF37445A)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M230.65,128.74c71.9,0,130.3,58.38,130.3,130.48v2.7H100.35v-2.7c0-72,58.3-130.48,130.3-130.48h0Z"
                ),
                fill = SolidColor(Color(0xFF4CE088)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M288.05,192.14c9.3,0,16.9,7.6,16.9,16.9s-7.6,16.9-16.9,16.9-16.9-7.6-16.9-16.9,7.6-16.9,16.9-16.9Z"
                ),
                fill = SolidColor(Color(0xFFF8F9FA)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M172.95,192.14c9.3,0,16.9,7.6,16.9,16.9s-7.6,16.9-16.9,16.9-16.9-7.6-16.9-16.9,7.6-16.9,16.9-16.9Z"
                ),
                fill = SolidColor(Color(0xFFF8F9FA)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M319.75,111.24l.8.5c9,5.4,12.1,17.4,6.9,26.8l-21.2,38.1c-5.2,9.4-16.8,12.6-25.8,7.2l-.8-.5c-9-5.4-12.1-17.4-6.9-26.8l21.2-38.1c5.2-9.4,16.8-12.6,25.8-7.2Z"
                ),
                fill = SolidColor(Color(0xFF4CE088)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M141.75,111.64l.8-.5c9-5.4,20.5-2.2,25.7,7.2l21.1,38.1c5.2,9.4,2.1,21.4-6.9,26.8l-.8.5c-9,5.4-20.5,2.2-25.7-7.2l-21.1-38.1c-5.2-9.4-2.1-21.4,6.9-26.8Z"
                ),
                fill = SolidColor(Color(0xFF4CE088)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M100.35,261.74h260.5v75.7c0,42.8-34.7,77.5-77.5,77.5h-105.5c-42.8,0-77.5-34.7-77.5-77.5,0,0,0-75.7,0-75.7Z"
                ),
                fill = SolidColor(Color(0xFF2A3950)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M268.35,261.74h92.6v82.9c0,38.8-31.5,70.3-70.3,70.3h-84.5v-93.36c0-20.68,1.74-36.77,22.42-59.84h39.78Z"
                ),
                fill = SolidColor(Color(0xFF37445A)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M276.35,311.44h39.2c4,0,7.2,3.2,7.2,7.2v1.9c0,4-3.2,7.2-7.2,7.2h-39.2c-4,0-7.2-3.2-7.2-7.2v-1.9c0-4,3.2-7.2,7.2-7.2Z"
                ),
                fill = SolidColor(Color(0xFFFFD83C)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M230.25,366.74c6,0,10.9,4.9,10.9,10.9s-4.9,10.9-10.9,10.9-10.9-4.9-10.9-10.9,4.9-10.9,10.9-10.9h0Z"
                ),
                fill = SolidColor(Color(0xFFFFD83C)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M230.25,334.04c6,0,10.9,4.9,10.9,10.9s-4.9,10.9-10.9,10.9-10.9-4.9-10.9-10.9,4.9-10.9,10.9-10.9h0Z"
                ),
                fill = SolidColor(Color(0xFFFFD83C)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M230.25,301.34c6,0,10.9,4.9,10.9,10.9s-4.9,10.9-10.9,10.9-10.9-4.9-10.9-10.9,4.9-10.9,10.9-10.9h0Z"
                ),
                fill = SolidColor(Color(0xFFFFD83C)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M360.95,261.68s-39.3,20-64.6,29.4c-25.3,9.4-38.7,6-49-6.2-10.2-12.2-17.1-23.2-17.1-23.2h130.7Z"
                ),
                fill = SolidColor(Color(0xFFF7F8F8)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M99.65,261.68s39.3,20,64.6,29.4c25.3,9.4,38.7,6,49-6.2,10.2-12.2,17.1-23.2,17.1-23.2H99.65Z"
                ),
                fill = SolidColor(Color(0xFFF7F8F8)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M114.81,136.24c-2.85-4.47-5.4-8.55-7.11-11.75-6.35-11.41-5-19.98-.6-26.09,6.71-9.31,12.73-18.2,35.7-28.57l.46-.2c23.2-9.6,34.21-8.67,45.61-7.37,7.48.85,15.68,5.54,18.89,18l.04.09c.81,3.14,1.85,7.23,3.06,11.73l3.6,13.68c.11.45.23.89.35,1.33,3.46,13.01,4.87,22.29,4.87,22.29-17.43,2.23-26.91,4.34-44.15,13.21-13.67,7.04-24.98,14.43-38.96,26.55,0,0-6.26-9.07-13.28-19.75-.27-.41-.54-.8-.81-1.21l-7.69-11.96Z"
                ),
                fill = SolidColor(Color(0xFF37445A)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M114.73,136.27c6.88-8.25,19.79-20.94,40.33-30.89l8.67-3.83c19.39-7.91,36.22-9.58,47.23-9.51l3.6,13.71c-35.6-1.01-69.87,14.85-92.12,42.5l-7.7-11.98Z"
                ),
                fill = SolidColor(Color(0xFFF9D43D)),
                pathFillType = PathFillType.EvenOdd,
            )
            addPath(
                pathData = addPathNodes(
                    "M25.36,290.07l2.46,3.28c4.54,5.96,12.95,7.12,18.96,2.67l14-10.57,5.13,6.79-9.66,7.3c-5.96,4.54-7.12,12.95-2.67,18.96l2.46,3.28c4.54,5.96,12.95,7.12,18.92,2.57l9.66-7.3,11.59,15.3c.04.09.12.27.26.32-11.9,17.19-12.07,40.72,1.25,58.42,16.55,21.95,47.75,26.24,69.69,9.69,21.95-16.55,26.24-47.75,9.69-69.69-13.28-17.6-36.03-23.83-55.82-17.13l-.16-.36-46.13-60.95c-4.54-5.96-12.95-7.12-18.96-2.67l-3.28,2.46c-1.06.81-1.95,1.75-2.71,2.75-1.19.53-2.28,1.02-3.34,1.83l-18.76,14.13c-5.96,4.54-7.12,12.95-2.67,18.96l.09-.04ZM125.19,344.65c8.92-6.75,21.65-5.01,28.53,3.96,6.75,8.92,5.01,21.65-3.96,28.53-8.92,6.75-21.65,5.01-28.53-3.96-6.75-8.92-5.05-21.74,3.96-28.53Z"
                ),
                fill = SolidColor(Color(0xFFFFD83E)),
                pathFillType = PathFillType.EvenOdd,
            )
        }.build().also { _porter = it }
    }

@Preview2
@Composable
private fun PorterPreview() {
    PreviewWrapper {
        Image(
            imageVector = SdmIcons.Porter,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

private var _porter: ImageVector? = null
