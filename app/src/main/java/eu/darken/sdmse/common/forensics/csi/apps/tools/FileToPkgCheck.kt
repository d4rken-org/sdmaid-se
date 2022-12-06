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
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Match a file name to a pkg
 * /data/app/somepkg-123.apk
 */
@Reusable
class FileToPkgCheck @Inject constructor(
    private val pkgRepo: PkgRepo,
) : CSISubProcessor {

    override suspend fun process(areaInfo: AreaInfo): CSISubProcessor.Result {
        val pkgId = CODESOURCE_FILE.matcher(areaInfo.file.name)
            .takeIf { it.matches() }
            ?.group(1)
            ?.toPkgId()
            ?: return CSISubProcessor.Result()

        val owners = if (pkgRepo.isInstalled(pkgId)) {
            setOf(Owner(pkgId))
        } else {
            emptySet()
        }
        return CSISubProcessor.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: FileToPkgCheck): CSISubProcessor
    }

    companion object {
        private val CODESOURCE_FILE = Pattern.compile("^([\\w.\\-]+)(?:\\-[0-9]{1,4}.apk)$")
    }
}