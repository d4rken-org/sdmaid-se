package eu.darken.sdmse.analyzer.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.ui.storage.device.DeviceStorageScreen
import eu.darken.sdmse.analyzer.ui.storage.device.DeviceStorageViewModel
import eu.darken.sdmse.analyzer.ui.storage.preview.previewDeviceStorage
import eu.darken.sdmse.analyzer.ui.storage.preview.previewStorageId
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Play Store screenshot entry point for the storage overview. Two storage rows (primary built-in +
// secondary SD card) with a Pro usage trend. previewSnapshots mirrors the private helper in
// DeviceStorageItemCard.kt (which the store screenshot's trend chart renders from).

private fun previewSnapshots(storageId: String): List<SpaceSnapshotEntity> {
    val capacity = 128L * 1024 * 1024 * 1024
    val base = Instant.parse("2026-05-25T12:00:00Z")
    val freeGb = listOf(48L, 47L, 45L, 46L, 44L, 43L, 42L)
    return freeGb.mapIndexed { index, gb ->
        SpaceSnapshotEntity(
            id = index.toLong(),
            storageId = storageId,
            recordedAt = base.plus(Duration.ofDays(index.toLong())),
            spaceFree = gb * 1024 * 1024 * 1024,
            spaceCapacity = capacity,
        )
    }
}

@PreviewTest
@PlayStoreLocales
@Composable
fun AnalyzerScreenshot() {
    PreviewWrapper {
        DeviceStorageScreen(
            stateSource = MutableStateFlow(
                DeviceStorageViewModel.State(
                    storages = listOf(
                        DeviceStorageViewModel.Row(
                            storage = previewDeviceStorage(
                                label = "Internal Storage",
                                type = DeviceStorage.Type.PRIMARY,
                                hardware = DeviceStorage.Hardware.BUILT_IN,
                                spaceCapacity = 128L * 1024 * 1024 * 1024,
                                spaceFree = 42L * 1024 * 1024 * 1024,
                            ),
                            snapshots = previewSnapshots("primary"),
                            isPro = true,
                        ),
                        DeviceStorageViewModel.Row(
                            storage = previewDeviceStorage(
                                id = previewStorageId(
                                    internalId = "secondary",
                                    externalId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                ),
                                label = "SD Card",
                                type = DeviceStorage.Type.SECONDARY,
                                hardware = DeviceStorage.Hardware.SDCARD,
                                spaceCapacity = 64L * 1024 * 1024 * 1024,
                                spaceFree = 18L * 1024 * 1024 * 1024,
                            ),
                            snapshots = previewSnapshots("secondary"),
                            isPro = true,
                        ),
                    ),
                ),
            ),
        )
    }
}
