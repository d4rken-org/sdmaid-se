package eu.darken.sdmse.corpsefinder.ui.preview

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.core.filter.PrivateDataCorpseFilter
import eu.darken.sdmse.corpsefinder.ui.list.CorpseFinderListViewModel
import java.time.Instant
import kotlin.reflect.KClass

internal fun previewLocalPathLookup(
    pathSegments: Array<String> = arrayOf("storage", "emulated", "0", "Android", "data", "com.example.app"),
    fileType: FileType = FileType.DIRECTORY,
    size: Long = 12L * 1024 * 1024,
): LocalPathLookup = LocalPathLookup(
    lookedUp = LocalPath.build(*pathSegments),
    fileType = fileType,
    size = size,
    modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
    target = null,
)

internal fun previewAreaInfo(): AreaInfo {
    val root = LocalPath.build("storage", "emulated", "0", "Android", "data")
    return AreaInfo(
        file = root,
        prefix = root,
        dataArea = DataArea(
            path = root,
            type = DataArea.Type.PRIVATE_DATA,
            label = "Private data".toCaString(),
            userHandle = UserHandle2(handleId = 0),
        ),
        isBlackListLocation = false,
    )
}

internal fun previewOwnerInfo(
    owners: Set<Owner> = setOf(
        Owner(pkgId = Pkg.Id(name = "com.example.app"), userHandle = UserHandle2(handleId = 0)),
    ),
): OwnerInfo = OwnerInfo(
    areaInfo = previewAreaInfo(),
    owners = owners,
    installedOwners = emptySet(),
    hasUnknownOwner = false,
)

internal fun previewCorpse(
    filterType: KClass<out eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter> = PrivateDataCorpseFilter::class,
    lookup: LocalPathLookup = previewLocalPathLookup(),
    content: Collection<APathLookup<*>> = listOf(
        previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "com.example.app", "cache.bin"),
            fileType = FileType.FILE,
            size = 8L * 1024 * 1024,
        ),
        previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "com.example.app", "old.log"),
            fileType = FileType.FILE,
            size = 4L * 1024 * 1024,
        ),
    ),
    riskLevel: RiskLevel = RiskLevel.NORMAL,
): Corpse = Corpse(
    filterType = filterType,
    ownerInfo = previewOwnerInfo(),
    lookup = lookup,
    content = content,
    riskLevel = riskLevel,
)

internal fun previewCorpseRow(
    corpse: Corpse = previewCorpse(),
): CorpseFinderListViewModel.Row = CorpseFinderListViewModel.Row(corpse = corpse)
