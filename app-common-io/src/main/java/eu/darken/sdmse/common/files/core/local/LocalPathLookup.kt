package eu.darken.sdmse.common.files.core.local

import eu.darken.sdmse.common.files.core.*
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Parcelize
data class LocalPathLookup(
    override val lookedUp: LocalPath,
    override val fileType: FileType,
    override val size: Long,
    override val modifiedAt: Instant,
    override val ownership: Ownership?,
    override val permissions: Permissions?,
    override val target: LocalPath?
) : APathLookup<LocalPath> {

    override fun child(vararg segments: String): APath = lookedUp.child(*segments)

    @IgnoredOnParcel override val path: String
        get() = lookedUp.path
    @IgnoredOnParcel override val name: String
        get() = lookedUp.name
    @IgnoredOnParcel override val segments: List<String>
        get() = lookedUp.segments
    @IgnoredOnParcel override val pathType: APath.PathType
        get() = lookedUp.pathType

    override fun toString(): String =
        "LocalPathLookup(path=$path, type=$fileType, size=$size, modified=$modifiedAt, owner=$ownership, perm=$permissions, target=$target)"

}