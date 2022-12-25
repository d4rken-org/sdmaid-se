package eu.darken.sdmse.common.files.core

import androidx.annotation.Keep
import java.time.Instant

@Keep
interface APathLookup<out T> : APath {
    val lookedUp: T
    val fileType: FileType
    val size: Long
    val modifiedAt: Instant
    val ownership: Ownership?
    val permissions: Permissions?
    val target: APath?
}