package eu.darken.sdmse.common.clutter

import androidx.annotation.Keep
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    @Serializable
    @Keep
    enum class Flag(val raw: String) {
        @SerialName("keeper") KEEPER("keeper"),
        @SerialName("common") COMMON("common"),
        @SerialName("custodian") CUSTODIAN("custodian"),
        ;
    }

    @Keep
    class Match(
        val packageNames: Set<Pkg.Id>,
        val flags: Set<Flag> = emptySet()
    ) {

        fun hasFlags(vararg toCheck: Flag): Boolean {
            return toCheck.any { flags.contains(it) }
        }

        override fun toString(): String = String.format("Match(pkg=%s, flags=%s", packageNames, flags)
    }
}