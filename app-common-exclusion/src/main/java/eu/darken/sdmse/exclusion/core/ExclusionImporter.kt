package eu.darken.sdmse.exclusion.core

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.exclusion.core.types.Exclusion
import javax.inject.Inject

class ExclusionImporter @Inject constructor(
    private val moshi: Moshi,
) {

    private val containerAdapter by lazy { moshi.adapter<Container>() }
    private val exclusionAdapter by lazy { moshi.adapter<Set<Exclusion>>() }

    suspend fun import(raw: String): Set<Exclusion> {
        if (raw.isEmpty()) throw IllegalArgumentException("Exclusion data was empty")

        try {
            val container = containerAdapter.fromJson(raw) ?: throw IllegalArgumentException("Exclusion data was empty")

            if (container.version != 1) throw IllegalArgumentException("Unsupported version: ${container.version}")

            return exclusionAdapter.fromJson(container.exclusionRaw)!!.also {
                log(TAG, VERBOSE) { "Imported ${it.size}\nINPUT: $raw\nOUTPUT: $it" }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Invalid data: ${e.asLog()}" }
            throw IllegalArgumentException("Invalid exclusion data", e)
        }
    }

    suspend fun export(exclusions: Set<Exclusion>): String {
        val container = Container(
            exclusionRaw = exclusionAdapter.toJson(exclusions)
        )
        return containerAdapter.toJson(container).also {
            log(TAG, VERBOSE) { "Exported ${exclusions.size}\nINPUT: $exclusions\nOUTPUT: $it" }
        }
    }


    @JsonClass(generateAdapter = true)
    data class Container(
        val exclusionRaw: String,
        val version: Int = 1,
    )

    companion object {
        private val TAG = logTag("Exclusion", "Importer")
    }
}