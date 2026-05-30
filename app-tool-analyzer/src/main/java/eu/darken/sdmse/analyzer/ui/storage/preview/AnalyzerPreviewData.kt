package eu.darken.sdmse.analyzer.ui.storage.preview

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.user.UserHandle2
import java.time.Instant
import java.util.UUID

private class PreviewInstalled(
    private val pkgName: String,
    private val displayLabel: String,
    override val userHandle: UserHandle2 = UserHandle2(handleId = 0),
) : Installed {
    override val packageInfo: PackageInfo = PackageInfo().apply {
        packageName = pkgName
        versionName = "1.0.0"
    }
    override val label: CaString = displayLabel.toCaString()
    override val icon: ((Context) -> Drawable)? = null
}

internal fun previewInstalled(
    pkgName: String = "com.example.app",
    label: String = "Example App",
): Installed = PreviewInstalled(pkgName = pkgName, displayLabel = label)

internal fun previewStorageId(
    internalId: String? = "primary",
    externalId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
): StorageId = StorageId(internalId = internalId, externalId = externalId)

internal fun previewDeviceStorage(
    id: StorageId = previewStorageId(),
    label: String = "Internal Storage",
    type: DeviceStorage.Type = DeviceStorage.Type.PRIMARY,
    hardware: DeviceStorage.Hardware = DeviceStorage.Hardware.BUILT_IN,
    spaceCapacity: Long = 128L * 1024 * 1024 * 1024,
    spaceFree: Long = 42L * 1024 * 1024 * 1024,
    setupIncomplete: Boolean = false,
): DeviceStorage = DeviceStorage(
    id = id,
    label = label.toCaString(),
    type = type,
    hardware = hardware,
    spaceCapacity = spaceCapacity,
    spaceFree = spaceFree,
    setupIncomplete = setupIncomplete,
)

internal fun previewContentItem(
    segments: Array<String> = arrayOf("storage", "emulated", "0", "DCIM", "photo.jpg"),
    type: FileType = FileType.FILE,
    size: Long = 4L * 1024 * 1024,
    children: Collection<ContentItem> = emptySet(),
    inaccessible: Boolean = false,
    withLookup: Boolean = true,
): ContentItem {
    val path = LocalPath.build(*segments)
    return ContentItem(
        path = path,
        lookup = if (withLookup) {
            LocalPathLookup(
                lookedUp = path,
                fileType = type,
                size = size,
                modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
                target = null,
            )
        } else {
            null
        },
        itemSize = size,
        type = type,
        children = children,
        inaccessible = inaccessible,
    )
}

internal fun previewContentGroup(
    label: String? = "App data",
    contents: Collection<ContentItem> = listOf(
        previewContentItem(segments = arrayOf("data", "data", "com.example.app", "databases", "main.db"), size = 12L * 1024 * 1024),
        previewContentItem(segments = arrayOf("data", "data", "com.example.app", "files", "blob.bin"), size = 6L * 1024 * 1024),
    ),
): ContentGroup = ContentGroup(
    label = label?.toCaString(),
    contents = contents,
)

internal fun previewPkgStat(
    pkg: Installed = previewInstalled(),
    appCode: ContentGroup? = previewContentGroup(label = "App code"),
    appData: ContentGroup? = previewContentGroup(label = "App data"),
    appMedia: ContentGroup? = previewContentGroup(label = "App media"),
    extraData: ContentGroup? = null,
): AppCategory.PkgStat = AppCategory.PkgStat(
    pkg = pkg,
    isShallow = false,
    appCode = appCode,
    appData = appData,
    appMedia = appMedia,
    extraData = extraData,
)
