package eu.darken.sdmse.systemcleaner.core.filter.custom.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs up user-created custom SystemCleaner filter definitions (the per-filter config files).
 * Which of them are *enabled* lives in SystemCleaner settings and is restored afterwards (this runs
 * at [ORDER_CONTENT][ConfigBackupContributor.ORDER_CONTENT], settings at the later default order).
 */
@Singleton
class CustomFiltersBackupContributor @Inject constructor(
    private val customFilterRepo: CustomFilterRepo,
    private val json: Json,
) : ConfigBackupContributor {

    override val key = "customfilters"
    override val restoreOrder = ConfigBackupContributor.ORDER_CONTENT

    private val serializer = SetSerializer(CustomFilterConfig.serializer())

    override suspend fun snapshot(): JsonElement? {
        val current = customFilterRepo.configs.first().toSet()
        if (current.isEmpty()) return null
        return json.encodeToJsonElement(serializer, current)
    }

    override suspend fun validate(data: JsonElement) {
        json.decodeFromJsonElement(serializer, data).forEach { config ->
            // Identifiers become file names inside the repo's filter dir — reject anything that
            // could traverse out of it (the repo's canonical-path check is the backstop).
            val id = config.identifier
            require(id.isNotBlank() && !id.contains('/') && !id.contains('\\') && !id.contains("..")) {
                "Invalid filter identifier: $id"
            }
        }
    }

    override suspend fun restore(data: JsonElement, mode: RestoreMode) {
        val restored = json.decodeFromJsonElement(serializer, data)
        when (mode) {
            // Atomic swap — never deletes existing filters before the new set is fully written.
            RestoreMode.REPLACE -> customFilterRepo.replaceAll(restored)
            RestoreMode.MERGE -> if (restored.isNotEmpty()) customFilterRepo.save(restored)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CustomFiltersBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: CustomFiltersBackupContributor): ConfigBackupContributor
}
