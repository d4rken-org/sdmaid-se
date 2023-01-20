package eu.darken.sdmse.appcleaner.core.forensics.sieves.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.areas.DataArea

@JsonClass(generateAdapter = true)
data class SieveJsonDb(
    @Json(name = "schemaVersion") val schemaVersion: Int,
    @Json(name = "appFilter") val appFilters: List<AppFilter>
) {

    init {
        if (appFilters.isEmpty()) throw IllegalStateException("App filters are empty")
    }

    @JsonClass(generateAdapter = true)
    data class AppFilter(
        @Json(name = "packages") val packageNames: Set<String>?,
        @Json(name = "fileFilter") val fileFilters: List<FileFilter>,
    ) {

        init {
            if (fileFilters.isEmpty()) throw IllegalStateException("File filters are empty")
        }

        @JsonClass(generateAdapter = true)
        data class FileFilter(
            @Json(name = "locations") val areaTypes: Set<DataArea.Type>,
            @Json(name = "startsWith") val startsWith: List<String>?,
            @Json(name = "contains") val contains: List<String>?,
            @Json(name = "notContains") val notContains: List<String>?,
            @Json(name = "patterns") val patterns: List<String>?,
        ) {
            init {
                if (startsWith == null && contains == null) throw IllegalStateException("Underdefined filter")
            }
        }

    }

}
