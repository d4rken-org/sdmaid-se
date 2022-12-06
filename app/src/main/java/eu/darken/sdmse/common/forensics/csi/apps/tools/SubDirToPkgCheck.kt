package eu.darken.sdmse.common.forensics.csi.apps.tools//package eu.thedarken.sdm.tools.forensics.csi.appapp

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
import eu.darken.sdmse.common.forensics.csi.CSISubProcessor
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
) : CSISubProcessor {
    override suspend fun process(areaInfo: AreaInfo): CSISubProcessor.Result {
        val potPkgNames = areaInfo.prefixFreePath.split(Pattern.quote(File.separator).toRegex()).toTypedArray()
        if (potPkgNames.isEmpty()) return CSISubProcessor.Result()

        val topDir = potPkgNames[0]
        val topDirMatcher = ANDROID11_TOPDIR.matcher(topDir)

        if (!topDirMatcher.matches()) return CSISubProcessor.Result()

        val subDir = try {
            areaInfo.file.listFiles(gatewaySwitch).singleOrNull()?.path ?: return CSISubProcessor.Result()
        } catch (e: Exception) {
            return CSISubProcessor.Result()
        }

        val owners = ANDROID11_SUBDIR.matcher(subDir)
            .takeIf { it.matches() }
            ?.group(1)
            ?.let { setOf(Owner(it.toPkgId())) }
            ?: emptySet()

        return CSISubProcessor.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SubDirToPkgCheck): CSISubProcessor
    }

    companion object {
        private val ANDROID11_TOPDIR = Pattern.compile("^(~~.+?==)\$")
        private val ANDROID11_SUBDIR = Pattern.compile("^([\\w.\\-]+)(?:-[a-zA-Z0-9=_-]{24})$")
    }
}