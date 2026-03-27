package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.exclusion.core.types.Exclusion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ExclusionImporter @Inject constructor(
    private val json: Json,
) {

    suspend fun import(raw: String): Set<Exclusion> {
        if (raw.isEmpty()) throw IllegalArgumentException("Exclusion data was empty")

        try {
            val container = json.decodeFromString<Container>(raw)

            if (container.version != 1) throw IllegalArgumentException("Unsupported version: ${container.version}")

            return json.decodeFromString<Set<Exclusion>>(container.exclusionRaw).also {
                log(TAG, VERBOSE) { "Imported ${it.size}\nINPUT: $raw\nOUTPUT: $it" }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Invalid data: ${e.asLog()}" }
            throw IllegalArgumentException("Invalid exclusion data", e)
        }
    }

    suspend fun export(exclusions: Set<Exclusion>): String {
        val container = Container(
            exclusionRaw = json.encodeToString(exclusions)
        )
        return json.encodeToString(container).also {
            log(TAG, VERBOSE) { "Exported ${exclusions.size}\nINPUT: $exclusions\nOUTPUT: $it" }
        }
    }


    @Serializable
    data class Container(
        val exclusionRaw: String,
        val version: Int = 1,
    )

    companion object {
        private val TAG = logTag("Exclusion", "Importer")
    }
}