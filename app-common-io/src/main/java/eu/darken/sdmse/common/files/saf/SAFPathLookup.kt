package eu.darken.sdmse.common.files.saf

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import java.time.Instant

data class SAFPathLookup(
    override val lookedUp: SAFPath,
    internal val docFile: SAFDocFile,
) : APathLookup<SAFPath> {

    override val fileType: FileType by lazy {
        when {
            docFile.isDirectory -> FileType.DIRECTORY
            else -> FileType.FILE
        }
    }

    override val size: Long by lazy { docFile.length }
    override val modifiedAt: Instant by lazy { docFile.lastModified }

    override val target: APath? = null
}