package eu.darken.sdmse.common.files.core.local

import eu.darken.sdmse.common.files.core.*
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class LocalPathLookup(
    override val lookedUp: LocalPath,
    override val fileType: FileType,
    override val size: Long,
    override val modifiedAt: Date,
    override val ownership: Ownership?,
    override val permissions: Permissions?,
    override val target: LocalPath?
) : APathLookup<LocalPath> {
    override fun child(vararg segments: String): eu.darken.sdmse.common.files.core.APath = lookedUp.child(*segments)

    @IgnoredOnParcel override val path: String = lookedUp.path
    @IgnoredOnParcel override val name: String = lookedUp.name
    @IgnoredOnParcel override val segments: List<String> = lookedUp.segments
    @IgnoredOnParcel override val pathType: eu.darken.sdmse.common.files.core.APath.PathType = lookedUp.pathType

}