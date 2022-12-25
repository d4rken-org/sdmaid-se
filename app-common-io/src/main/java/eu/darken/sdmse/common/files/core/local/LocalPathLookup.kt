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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocalPathLookup) return false

        if (lookedUp != other.lookedUp) return false
        if (fileType != other.fileType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lookedUp.hashCode()
        result = 31 * result + fileType.hashCode()
        return result
    }
}