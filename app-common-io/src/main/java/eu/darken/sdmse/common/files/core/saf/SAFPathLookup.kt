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

    override fun child(vararg segments: String): SAFPath = lookedUp.child(*segments)

    @IgnoredOnParcel override val path: String
        get() = lookedUp.path

    @IgnoredOnParcel override val name: String
        get() = lookedUp.name

    @IgnoredOnParcel override val segments: List<String>
        get() = lookedUp.segments

    @IgnoredOnParcel override val pathType: APath.PathType
        get() = lookedUp.pathType

}