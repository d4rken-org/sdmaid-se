package eu.darken.sdmse.common.clutter

import androidx.annotation.Keep
import com.squareup.moshi.Json
import eu.darken.sdmse.common.storageareas.StorageArea

@Keep
interface Marker {

    val areaType: StorageArea.Type

    val flags: Set<Flag>

    fun match(areaType: StorageArea.Type, prefixFree: String): Match?

    val prefixFreeBasePath: String

    val isPrefixFreeBasePathDirect: Boolean

    @Keep
    enum class Flag(val raw: String) {
        @Json(name = "keeper") KEEPER("keeper"),
        @Json(name = "common") COMMON("common"),
        @Json(name = "custodian") CUSTODIAN("custodian"),
        ;
    }

    @Keep
    class Match constructor(
        val packageNames: Set<String>,
        val flags: Set<Flag> = emptySet()
    ) {

        fun hasFlags(vararg toCheck: Flag): Boolean {
            return toCheck.any { flags.contains(it) }
        }

        override fun toString(): String = String.format("Match(pkg=%s, flags=%s", packageNames, flags)
    }
}