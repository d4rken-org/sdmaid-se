package eu.darken.sdmse.common.forensics.csi.apps.tools

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.CSISubProcessor
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import javax.inject.Inject

@Reusable
class DirectApkCheck @Inject constructor(
    private val pkgOps: PkgOps,
    private val pkgRepo: PkgRepo
) : CSISubProcessor {

    override suspend fun process(areaInfo: AreaInfo): CSISubProcessor.Result {
        if (!areaInfo.file.name.endsWith(".apk")) return CSISubProcessor.Result()

        val info = pkgOps.viewArchive(areaInfo.file, 0)

        val owners = if (info != null) {
            setOf(Owner(info.id))
        } else {
            emptySet()
        }
        return CSISubProcessor.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DirectApkCheck): CSISubProcessor
    }
}