package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import javax.inject.Inject

@Reusable
class OddOnesCheck @Inject constructor(
    private val runtimeTool: RuntimeTool,
) : DalvikCheck {

    suspend fun check(areaInfo: AreaInfo): DalvikCheck.Result {
        val fileName: String = areaInfo.file.name
        var unknownOwner = false
        if (fileName == "minimode.dex") {
            unknownOwner = true
        } else if (runtimeTool.getRuntimeInfo().type == RuntimeTool.Info.Type.ART) {
            if (fileName.contains("boot.art") || fileName.contains("boot.oat")) {
                unknownOwner = true
            }
        }

        return DalvikCheck.Result(
            hasKnownUnknownOwner = unknownOwner
        )
    }

    companion object {
        val TAG: String = logTag("CSI", "Dalvik", "Dex", "OddOnes")
    }
}