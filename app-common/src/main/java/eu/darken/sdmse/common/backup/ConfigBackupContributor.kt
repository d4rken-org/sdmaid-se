package eu.darken.sdmse.common.backup

import kotlinx.serialization.json.JsonElement

/**
 * Contributes one section of the user's configuration to a config backup, and restores it.
 *
 * Implementations are bound `@IntoSet` (mirroring [eu.darken.sdmse.common.navigation.NavigationEntry])
 * and aggregated by [ConfigBackupManager]. Each contributor owns a stable [key] that namespaces its
 * payload inside the backup envelope, so adding/removing a contributor across app versions never
 * invalidates an existing backup — unknown/missing sections are simply skipped on restore.
 */
interface ConfigBackupContributor {

    /** Stable, unique identifier used as the section key inside the backup envelope. */
    val key: String

    /**
     * Restore priority — lower runs first. Content stores (exclusions, schedules, custom filters)
     * use [ORDER_CONTENT] so they exist before settings that reference them (e.g. the
     * SystemCleaner `enabledCustomFilter` set) are restored at [ORDER_SETTINGS].
     */
    val restoreOrder: Int get() = ORDER_SETTINGS

    /**
     * Produce this contributor's current configuration, or `null` if there is nothing to back up
     * (e.g. everything is at its default). A `null` section is omitted from the envelope.
     */
    suspend fun snapshot(): JsonElement?

    /**
     * Side-effect-free check that [data] can be applied. Called for every present section before
     * ANY section is applied, so a broken archive fails the whole restore up front instead of
     * mid-apply. Implementations should decode exactly as [restore] would, without writing anything,
     * and throw on data that [restore] would reject.
     */
    suspend fun validate(data: JsonElement) {
        // Default: the manager already parsed the section as JSON; contributors with a stricter
        // shape (or key-level fault tolerance) override this.
    }

    /** Apply a previously [snapshot]ed section back onto the live configuration. */
    suspend fun restore(data: JsonElement, mode: RestoreMode)

    companion object {
        const val ORDER_CONTENT = 0
        const val ORDER_SETTINGS = 100
    }
}

enum class RestoreMode {
    /** Apply backup values on top of the current config; never deletes existing data. */
    MERGE,

    /** Reset the contributor's managed config, then apply the backup verbatim. */
    REPLACE,
}
