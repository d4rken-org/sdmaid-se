package eu.darken.sdmse.exclusion.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.exclusion.core.ExclusionImporter
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.ExclusionStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Backs up the user's own exclusions (the user-created set, reusing the versioned [ExclusionImporter]). */
@Singleton
class ExclusionsBackupContributor @Inject constructor(
    private val exclusionStorage: ExclusionStorage,
    private val exclusionManager: ExclusionManager,
    private val exclusionImporter: ExclusionImporter,
    private val json: Json,
) : ConfigBackupContributor {

    override val key = "exclusions"
    override val restoreOrder = ConfigBackupContributor.ORDER_CONTENT

    // ExclusionImporter speaks String (its own versioned Container). Parse that into a real JsonElement
    // so the section is inspectable, pretty-printable JSON instead of a quoted string-of-JSON.
    override suspend fun snapshot(): JsonElement? {
        val current = exclusionStorage.load() ?: emptySet()
        if (current.isEmpty()) return null
        return json.parseToJsonElement(exclusionImporter.export(current))
    }

    override suspend fun restore(data: JsonElement, mode: RestoreMode) {
        // Accept both the new object shape and the legacy quoted-string shape (older backups).
        val payload = if (data is JsonPrimitive && data.isString) data.content else data.toString()
        val restored = exclusionImporter.import(payload)
        when (mode) {
            RestoreMode.REPLACE -> exclusionManager.replaceUserExclusions(restored)
            RestoreMode.MERGE -> exclusionManager.save(restored)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExclusionsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: ExclusionsBackupContributor): ConfigBackupContributor
}
