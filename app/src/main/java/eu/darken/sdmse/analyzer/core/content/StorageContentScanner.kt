package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.analyzer.core.content.types.AppContent
import eu.darken.sdmse.analyzer.core.content.types.MediaContent
import eu.darken.sdmse.analyzer.core.content.types.StorageContent
import eu.darken.sdmse.analyzer.core.content.types.SystemContent
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

class StorageContentScanner @Inject constructor(

) {
    suspend fun scan(storageId: DeviceStorage.Id): Collection<StorageContent> {
        log(TAG) { "scan($storageId)" }

        val app = AppContent(
            id = storageId.value + ".app",
            spaceUsed = 1024L * 1024 * 1024L * 39,
        )
        val media = MediaContent(
            id = storageId.value + ".media",
            spaceUsed = 1024L * 1024 * 1024L * 24,
        )
        val system = SystemContent(
            id = storageId.value + ".system",
            spaceUsed = 1024L * 1024 * 1024L * 11,
        )
        return setOf(app, media, system)
    }

    companion object {
        private val TAG = logTag("Analyzer", "DeviceStorage", "Scanner")
    }
}