package eu.darken.sdmse.common.files

import androidx.annotation.Keep

@Keep
interface APathLookupExtended<out T : APath> : APathLookup<T> {
    val ownership: Ownership?
    val permissions: Permissions?
}