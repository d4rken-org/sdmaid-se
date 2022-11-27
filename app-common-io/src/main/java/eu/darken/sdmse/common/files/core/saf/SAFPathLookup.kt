package eu.darken.sdmse.common.files.core.saf

import eu.darken.sdmse.common.files.core.*
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class SAFPathLookup(
    override val lookedUp: SAFPath,
    override val size: Long,
    override val modifiedAt: Date,
    override val ownership: Ownership?,
    override val permissions: Permissions?,
    override val fileType: FileType,
    override val target: SAFPath?
) : APathLookup<SAFPath> {

    override fun child(vararg segments: String): SAFPath {
        return lookedUp.child(*segments)
    }

    @IgnoredOnParcel
    override val path: String = lookedUp.path

    @IgnoredOnParcel
    override val name: String = lookedUp.name

    @IgnoredOnParcel
    override val segments: List<String> = lookedUp.segments

    @IgnoredOnParcel
    override val pathType: eu.darken.sdmse.common.files.core.APath.PathType = lookedUp.pathType

}