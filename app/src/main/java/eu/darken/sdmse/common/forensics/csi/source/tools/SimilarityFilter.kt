package eu.darken.sdmse.common.forensics.csi.source.tools

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.getFirstDirElement
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject


@Reusable
class SimilarityFilter @Inject constructor(
    private val pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo,
) {

    suspend fun filterFalsePositives(areaInfo: AreaInfo, toCheck: Collection<Owner>): Collection<Owner> {
        // com.mxtech.ffmpeg.x86-3 || com.mxtech.ffmpeg.x86-tmEGrx2zM5CeRFI72KWLSA==
        val firstDirName = areaInfo.prefixFreePath.getFirstDirElement()
        val firstDirPath = LocalPath.build(areaInfo.prefix, firstDirName).path

        val firstDirHyphenPath = run {
            val hyphenPos = firstDirName.indexOf('-')
            var firstDirNameHyphen = firstDirName
            if (hyphenPos != -1 && hyphenPos + 1 <= firstDirName.length) {
                // com.mxtech.ffmpeg.x86-
                firstDirNameHyphen = firstDirName.substring(0, hyphenPos + 1)
            }
            LocalPath.build(areaInfo.prefix, firstDirNameHyphen).path
        }

        // https://github.com/d4rken/sdmaid-public/issues/996
        // Do we have an owner that could falsely match this, despite not using it?
        return toCheck.filter { candidate ->

            val sourceDir = pkgRepo.getPkg(candidate.pkgId)
                ?.let { it as? Installed }
                ?.sourceDir ?: return@filter true

            if (sourceDir.path.startsWith(firstDirHyphenPath) && !sourceDir.path.startsWith(firstDirPath)) {
                // /data/app/some.pkg-3 starts with /data/app/some.pkg- but not with /data/app/some.pkg-2
                log(TAG) {
                    "False positive match. Removing $candidate, because it's the owner of $sourceDir, not $areaInfo"
                }
                false
            } else {
                true
            }
        }
    }

    companion object {
        val TAG: String = logTag("CSI", "App", "Tools", "SimilarityCheck")
    }
}