package eu.darken.sdmse.appcleaner.ui.preview

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.ui.list.AppCleanerListViewModel
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2
import java.time.Instant

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

private fun previewLookup(
    pathSegments: Array<String>,
    fileType: FileType = FileType.FILE,
    size: Long = 1024L * 1024,
): LocalPathLookup = LocalPathLookup(
    lookedUp = LocalPath.build(*pathSegments),
    fileType = fileType,
    size = size,
    modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
    target = null,
)

internal fun previewExpendables(): Map<kotlin.reflect.KClass<out ExpendablesFilter>, Collection<ExpendablesFilter.Match>> =
    mapOf(
        DefaultCachesPublicFilter::class to listOf(
            ExpendablesFilter.Match.Deletion(
                identifier = DefaultCachesPublicFilter::class,
                lookup = previewLookup(
                    pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "com.example.app", "cache", "blob.bin"),
                    size = 6L * 1024 * 1024,
                ),
            ),
            ExpendablesFilter.Match.Deletion(
                identifier = DefaultCachesPublicFilter::class,
                lookup = previewLookup(
                    pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "com.example.app", "cache", "old.tmp"),
                    size = 2L * 1024 * 1024,
                ),
            ),
        ),
    )

internal fun previewInaccessibleCache(
    pkgName: String = "com.example.app",
): InaccessibleCache = InaccessibleCache(
    identifier = eu.darken.sdmse.common.pkgs.features.InstallId(
        pkgId = pkgName.toPkgId(),
        userHandle = UserHandle2(handleId = 0),
    ),
    isSystemApp = false,
    itemCount = 12,
    totalSize = 24L * 1024 * 1024,
    publicSize = null,
    theoreticalPaths = emptySet(),
)

internal fun previewAppJunk(
    pkg: Installed = previewInstalled(),
    expendables: Map<kotlin.reflect.KClass<out ExpendablesFilter>, Collection<ExpendablesFilter.Match>>? = previewExpendables(),
    inaccessibleCache: InaccessibleCache? = previewInaccessibleCache(),
): AppJunk = AppJunk(
    pkg = pkg,
    userProfile = null,
    expendables = expendables,
    inaccessibleCache = inaccessibleCache,
)

internal fun previewAppCleanerRow(
    junk: AppJunk = previewAppJunk(),
): AppCleanerListViewModel.Row = AppCleanerListViewModel.Row(junk = junk)
