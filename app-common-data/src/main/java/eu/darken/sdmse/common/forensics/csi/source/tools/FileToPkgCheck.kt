package eu.darken.sdmse.common.forensics.csi.source.tools

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.source.AppSourceCheck
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

/**
 * Match a file name to a pkg
 * /data/app/somepkg-123.apk
 */
@Reusable
class FileToPkgCheck @Inject constructor(
    private val pkgRepo: PkgRepo,
) : AppSourceCheck {

    override suspend fun process(areaInfo: AreaInfo): AppSourceCheck.Result {
        val pkgId = CODESOURCE_FILE.matchEntire(areaInfo.file.name)
            ?.groupValues?.get(1)
            ?.toPkgId()
            ?: return AppSourceCheck.Result()

        val userHandle = areaInfo.userHandle
        val owners = if (pkgRepo.isInstalled(pkgId, userHandle)) {
            setOf(Owner(pkgId, userHandle))
        } else {
            emptySet()
        }
        return AppSourceCheck.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: FileToPkgCheck): AppSourceCheck
    }

    companion object {
        private val CODESOURCE_FILE by lazy { Regex("^([\\w.\\-]+)-[0-9]{1,4}.apk$") }
    }
}