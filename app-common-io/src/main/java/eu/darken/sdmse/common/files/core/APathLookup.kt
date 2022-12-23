package eu.darken.sdmse.common.files.core

import androidx.annotation.Keep
import java.util.*

@Keep
interface APathLookup<out T> : APath {
    val lookedUp: T
    val fileType: FileType
    val size: Long
    val modifiedAt: Date
    val ownership: Ownership?
    val permissions: Permissions?
    val target: APath?
}