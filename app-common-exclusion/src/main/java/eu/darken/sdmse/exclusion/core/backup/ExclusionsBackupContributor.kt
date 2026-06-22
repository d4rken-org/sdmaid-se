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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Backs up the user's own exclusions (the user-created set, reusing the versioned [ExclusionImporter]). */
@Singleton
class ExclusionsBackupContributor @Inject constructor(
    private val exclusionStorage: ExclusionStorage,
    private val exclusionManager: ExclusionManager,
    private val exclusionImporter: ExclusionImporter,
) : ConfigBackupContributor {

    override val key = "exclusions"
    override val restoreOrder = ConfigBackupContributor.ORDER_CONTENT

    override suspend fun snapshot(): JsonElement? {
        val current = exclusionStorage.load() ?: emptySet()
        if (current.isEmpty()) return null
        return JsonPrimitive(exclusionImporter.export(current))
    }

    override suspend fun restore(data: JsonElement, mode: RestoreMode) {
        val restored = exclusionImporter.import(data.jsonPrimitive.content)
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
