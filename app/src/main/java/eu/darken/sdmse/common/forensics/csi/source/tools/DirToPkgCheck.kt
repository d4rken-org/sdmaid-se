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
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Match a dir and parse it to a packagename
 * some_pkg-1234
 * some_pkg-1234/something
 * some_pkg--tmEGrx2zM5CeRFI72KWLSA==
 * some_pkg--tmEGrx2zM5CeRFI72KWLSA==/something
 */
@Reusable
class DirToPkgCheck @Inject constructor(
    private val pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo,
) : AppSourceCheck {

    override suspend fun process(areaInfo: AreaInfo): AppSourceCheck.Result {
        val potPkgNames = areaInfo.prefixFreePath.split(File.separator)
        if (potPkgNames.isEmpty()) return AppSourceCheck.Result()

        val owners = listOf(CODESOURCE_DIR, APPDIR_ANDROIDO)
            .asSequence()
            .map { it.matcher(potPkgNames[0]) }
            .firstOrNull { it.matches() }
            ?.group(1)
            ?.toPkgId()
            ?.takeIf { pkgRepo.isInstalled(it) }
            ?.let { setOf(Owner(it)) }
            ?: emptySet()
        return AppSourceCheck.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DirToPkgCheck): AppSourceCheck
    }

    companion object {
        private val CODESOURCE_DIR = Pattern.compile("^([\\w.\\-]+)(?:\\-[0-9]{1,4})$")
        private val APPDIR_ANDROIDO = Pattern.compile("^([\\w.\\-]+)(?:\\-[a-zA-Z0-9=_-]{24})$")
    }
}