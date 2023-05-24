package eu.darken.sdmse.common.files

import androidx.annotation.Keep
import eu.darken.sdmse.common.ca.CaString
import java.time.Instant

@Keep
interface APathLookup<out T : APath> {
    val lookedUp: T
    val fileType: FileType
    val size: Long
    val modifiedAt: Instant
    val target: APath?

    val path: String
        get() = lookedUp.path
    val name: String
        get() = lookedUp.name
    val pathType: APath.PathType
        get() = lookedUp.pathType
    val userReadablePath: CaString
        get() = lookedUp.userReadablePath
    val userReadableName: CaString
        get() = lookedUp.userReadableName

    val segments: Segments
        get() = lookedUp.segments

    fun child(vararg segments: String): APath = lookedUp.child(*segments)
}