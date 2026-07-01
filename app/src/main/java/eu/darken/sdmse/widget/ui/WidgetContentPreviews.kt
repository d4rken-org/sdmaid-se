@file:OptIn(ExperimentalGlancePreviewApi::class)

package eu.darken.sdmse.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import eu.darken.sdmse.widget.WidgetRenderState
import eu.darken.sdmse.widget.WidgetRenderState.Data.StorageEntry

// Glance @Preview functions render in Android Studio's design pane (not JVM/terminal).
// Sizes roughly mirror the widget's minimum and a larger placement.

private const val GB = 1_000_000_000L

private fun internal(usedGb: Long, totalGb: Long) =
    StorageEntry(StorageEntry.Kind.INTERNAL, usedGb * GB, totalGb * GB)

private fun external(usedGb: Long, totalGb: Long) =
    StorageEntry(StorageEntry.Kind.EXTERNAL, usedGb * GB, totalGb * GB)

@Preview(widthDp = 200, heightDp = 140)
@Composable
private fun WidgetContentNormalPreview() {
    WidgetContent(WidgetRenderState.Data(listOf(internal(45, 128)), freedBytes = 12 * GB))
}

@Preview(widthDp = 320, heightDp = 80)
@Composable
private fun WidgetContentSingleRowPreview() {
    WidgetContent(WidgetRenderState.Data(listOf(internal(45, 128)), freedBytes = 12 * GB))
}

@Preview(widthDp = 200, heightDp = 140)
@Composable
private fun WidgetContentNearFullPreview() {
    WidgetContent(WidgetRenderState.Data(listOf(internal(121, 128)), freedBytes = 3 * GB))
}

@Preview(widthDp = 220, heightDp = 170)
@Composable
private fun WidgetContentWithSdCardPreview() {
    WidgetContent(
        WidgetRenderState.Data(
            storages = listOf(internal(45, 128), external(20, 64)),
            freedBytes = 12 * GB,
        )
    )
}

@Preview(widthDp = 200, heightDp = 140)
@Composable
private fun WidgetContentUnavailablePreview() {
    WidgetContent(WidgetRenderState.Unavailable)
}

@Preview(widthDp = 320, heightDp = 190)
@Composable
private fun WidgetContentLargePreview() {
    WidgetContent(
        WidgetRenderState.Data(
            storages = listOf(internal(45, 128), external(20, 64)),
            freedBytes = 12 * GB,
        )
    )
}
