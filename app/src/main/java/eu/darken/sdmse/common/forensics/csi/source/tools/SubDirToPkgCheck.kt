package eu.darken.sdmse.common.forensics.csi.source.tools//package eu.thedarken.sdm.tools.forensics.csi.appapp

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.listFiles
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.source.AppSourceCheck
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Android 11
 * Match a subdir and parse it to a packagename
 * /data/app/~~es531jiysQU2KzlT50xW5g==/eu.thedarken.sdm-EAWVrZpVQXlEsYjs52qyBQ==/base.apk
 */
@Reusable
class SubDirToPkgCheck @Inject constructor(
    private val gatewaySwitch: GatewaySwitch
) : AppSourceCheck {
    override suspend fun process(areaInfo: AreaInfo): AppSourceCheck.Result {
        if (!hasApiLevel(30)) return AppSourceCheck.Result()

        val potPkgNames = areaInfo.prefixFreePath.split(Pattern.quote(File.separator).toRegex()).toTypedArray()
        if (potPkgNames.isEmpty()) return AppSourceCheck.Result()

        val topDir = potPkgNames[0]
        val topDirMatcher = ANDROID11_TOPDIR.matcher(topDir)

        if (!topDirMatcher.matches()) return AppSourceCheck.Result()

        val subDir = try {
            areaInfo.file.listFiles(gatewaySwitch).singleOrNull()?.path ?: return AppSourceCheck.Result()
        } catch (e: Exception) {
            return AppSourceCheck.Result()
        }

        val owners = ANDROID11_SUBDIR.matcher(subDir)
            .takeIf { it.matches() }
            ?.group(1)
            ?.let { setOf(Owner(it.toPkgId())) }
            ?: emptySet()

        return AppSourceCheck.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SubDirToPkgCheck): AppSourceCheck
    }

    companion object {
        private val ANDROID11_TOPDIR = Pattern.compile("^(~~.+?==)\$")
        private val ANDROID11_SUBDIR = Pattern.compile("^([\\w.\\-]+)(?:-[a-zA-Z0-9=_-]{24})$")
    }
}