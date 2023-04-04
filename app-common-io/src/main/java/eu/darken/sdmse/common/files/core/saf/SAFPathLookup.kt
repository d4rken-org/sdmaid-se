package eu.darken.sdmse.common.files.core.saf

import eu.darken.sdmse.common.files.core.*
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

    private val fstat by lazy { docFile.fstat() }
    override val ownership: Ownership? by lazy {
        fstat?.let { Ownership(it.st_uid.toLong(), it.st_gid.toLong()) }
    }
    override val permissions: Permissions? by lazy {
        fstat?.let { Permissions(it.st_mode) }
    }

    override val target: APath? = null
}