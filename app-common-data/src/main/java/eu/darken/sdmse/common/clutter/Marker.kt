package eu.darken.sdmse.common.clutter

import androidx.annotation.Keep
import com.squareup.moshi.Json
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.pkgs.Pkg

@Keep
interface Marker {

    val areaType: DataArea.Type

    val flags: Set<Flag>

    val segments: List<String>

    /**
     * i.e. no regex match
     */
    val isDirectMatch: Boolean

    /**
     * e.g.
     * /storage/emulated/0/Some/Folder
     * ->
     * Type.SDCARD [Some,Folder]
     */
    fun match(otherAreaType: DataArea.Type, otherSegments: Segments): Match?

    @Keep
    enum class Flag(val raw: String) {
        @Json(name = "keeper") KEEPER("keeper"),
        @Json(name = "common") COMMON("common"),
        @Json(name = "custodian") CUSTODIAN("custodian"),
        ;
    }

    @Keep
    class Match constructor(
        val packageNames: Set<Pkg.Id>,
        val flags: Set<Flag> = emptySet()
    ) {

        fun hasFlags(vararg toCheck: Flag): Boolean {
            return toCheck.any { flags.contains(it) }
        }

        override fun toString(): String = String.format("Match(pkg=%s, flags=%s", packageNames, flags)
    }
}