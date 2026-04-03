package eu.darken.sdmse.analyzer.core.storage

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.storage.StorageId
import dagger.Reusable
import javax.inject.Inject

@Reusable
class SystemStorageScanner @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
) {

    suspend fun scan(
        storageId: StorageId,
        existingGroupId: ContentGroup.Id,
        spaceUsedOverride: Long?,
        dataAreas: Set<DataArea>,
    ): SystemCategory {
        log(TAG) { "scan(): storageId=$storageId" }

        val systemItems = walkSystemPartitions()
        val excludedDataDirs = buildExcludedDataDirs(dataAreas)
        val dataItem = walkData(excludedDataDirs)

        log(TAG) { "scan(): excludedDataDirs=$excludedDataDirs" }

        val walkedItems = systemItems.plus(dataItem)
        // Always browsable — we only reach scan() when root/ADB access is available
        val isBrowsable = true

        val contentItems = addRemainder(walkedItems, spaceUsedOverride)

        val group = ContentGroup(
            id = existingGroupId,
            label = eu.darken.sdmse.analyzer.R.string.analyzer_storage_content_type_system_label.toCaString(),
            contents = contentItems,
        )

        return SystemCategory(
            storageId = storageId,
            groups = setOf(group),
            spaceUsedOverride = spaceUsedOverride,
            isBrowsable = isBrowsable,
            isDeepScanned = true,
        )
    }

    private suspend fun walkSystemPartitions(): List<ContentItem> {
        return SYSTEM_PARTITIONS.mapNotNull { partition ->
            val path = LocalPath.build(partition)
            try {
                val lookup = gatewaySwitch.lookup(path, type = GatewaySwitch.Type.AUTO)
                if (lookup.fileType == FileType.DIRECTORY) {
                    path.walkContentItem(gatewaySwitch, maxItems = WALK_MAX_ITEMS, followSymlinks = true)
                } else {
                    log(TAG) { "walkSystemPartitions(): /$partition is not a directory (${lookup.fileType}), skipping" }
                    null
                }
            } catch (e: ReadException) {
                log(TAG, WARN) { "walkSystemPartitions(): Failed to walk /$partition: ${e.asLog()}" }
                null
            }
        }
    }

    private suspend fun walkData(excludedDirs: Set<String>): ContentItem {
        return try {
            val dataPath = LocalPath.build("data")
            val dataLookup = gatewaySwitch.lookup(dataPath, type = GatewaySwitch.Type.AUTO)
            val dataChildren = gatewaySwitch.listFiles(dataPath)
            val filteredChildren = dataChildren.filter { it.name !in excludedDirs }

            log(TAG) { "walkData(): children=${dataChildren.map { it.name }}, filtered=${filteredChildren.map { it.name }}" }

            val walkedChildren = filteredChildren.map { child ->
                try {
                    child.walkContentItem(gatewaySwitch, maxItems = WALK_MAX_ITEMS, followSymlinks = true)
                } catch (e: ReadException) {
                    log(TAG, WARN) { "walkData(): Failed to walk /data/${child.name}: ${e.asLog()}" }
                    ContentItem.fromInaccessible(child)
                }
            }

            walkedChildren.plus(ContentItem.fromLookup(dataLookup)).toNestedContent().single()
        } catch (e: ReadException) {
            log(TAG, WARN) { "walkData(): Failed to access /data, size will be attributed to Other: ${e.asLog()}" }
            ContentItem.fromInaccessible(LocalPath.build("data"))
        }
    }

    companion object {
        private val TAG = logTag("Analyzer", "Storage", "Scanner", "System")

        private const val WALK_MAX_ITEMS = 100_000

        val OTHER_PATH = LocalPath.build("other")

        private val SYSTEM_PARTITIONS = listOf("system", "vendor", "product", "system_ext", "odm")

        /**
         * /data subdirectories already covered by AppCategory or MediaCategory.
         * These are excluded from the system walk because their space is already subtracted
         * from the residual (spaceUsedOverride = totalUsed - apps - media).
         *
         * Hardcoded entries cover dirs that map to DataAreas through different paths
         * (e.g., on API 30+, PRIVATE_DATA points to /data_mirror, not /data/data).
         * Dynamic entries are derived from actual DataArea paths.
         */
        fun buildExcludedDataDirs(dataAreas: Set<DataArea>): Set<String> = buildSet {
            // Hardcoded: bind mounts and dirs whose DataArea paths don't match /data/*
            // /data/data and /data/user → app private data (PRIVATE_DATA maps to /data_mirror on API 30+)
            // /data/user_de → device-encrypted app data (same issue)
            // /data/media → emulated storage backing (MediaCategory walks /storage/emulated/0 instead)
            addAll(setOf("data", "user", "user_de", "media"))

            // Dynamic: only app-related DataArea types contribute exclusions
            val appCoveredTypes = setOf(
                DataArea.Type.APP_APP,
                DataArea.Type.APP_APP_PRIVATE,
                DataArea.Type.APP_LIB,
                DataArea.Type.APP_ASEC,
                DataArea.Type.PRIVATE_DATA,
            )
            for (area in dataAreas) {
                if (area.type !in appCoveredTypes) continue
                val segs = area.path.segments
                val dataIdx = segs.indexOf("data")
                if (dataIdx >= 0 && dataIdx + 1 < segs.size) {
                    add(segs[dataIdx + 1])
                }
            }
        }

        /**
         * Add an "Other" remainder entry so the content view total matches the card value.
         * The remainder covers filesystem overhead, SELinux-protected content, and
         * other space that can't be walked but is part of the residual calculation.
         */
        fun addRemainder(walkedItems: List<ContentItem>, spaceUsedOverride: Long?): Set<ContentItem> {
            val walkedSize = walkedItems.sumOf { it.size ?: 0L }
            val remainder = (spaceUsedOverride ?: 0L) - walkedSize
            return if (remainder > 0L) {
                log(TAG) { "addRemainder(): remainder=$remainder, walked=$walkedSize, total=$spaceUsedOverride" }
                walkedItems.plus(
                    ContentItem(
                        path = OTHER_PATH,
                        lookup = null,
                        label = eu.darken.sdmse.analyzer.R.string.analyzer_storage_content_type_system_other_label.toCaString(),
                        type = FileType.DIRECTORY,
                        itemSize = remainder,
                        inaccessible = true,
                    )
                ).toSet()
            } else {
                walkedItems.toSet()
            }
        }
    }
}
