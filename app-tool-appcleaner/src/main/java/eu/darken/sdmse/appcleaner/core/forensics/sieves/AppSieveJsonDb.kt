package eu.darken.sdmse.appcleaner.core.forensics.sieves

import eu.darken.sdmse.common.areas.DataArea
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppSieveJsonDb(
    @SerialName("schemaVersion") val schemaVersion: Int,
    @SerialName("appFilter") val appFilters: List<AppFilter>,
) {

    init {
        if (appFilters.isEmpty()) throw IllegalStateException("App filters are empty")
    }

    @Serializable
    data class AppFilter(
        @SerialName("packages") val packageNames: Set<String>?,
        @SerialName("fileFilter") val fileFilters: List<FileFilter>,
    ) {

        init {
            if (fileFilters.isEmpty()) throw IllegalStateException("File filters are empty")
        }

        @Serializable
        data class FileFilter(
            @SerialName("locations") val areaTypes: Set<DataArea.Type>,
            @SerialName("startsWith") val startsWith: List<String>? = null,
            @SerialName("contains") val contains: List<String>? = null,
            @SerialName("notContains") val notContains: List<String>? = null,
            @SerialName("patterns") val patterns: List<String>? = null,
        ) {
            init {
                if (startsWith == null && contains == null) throw IllegalStateException("Underdefined filter")
            }
        }

    }

}
