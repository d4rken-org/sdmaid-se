package eu.darken.sdmse.common.backup

import eu.darken.sdmse.common.serialization.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * `manifest.json` of a backup archive: provenance metadata only.
 *
 * Versioned like [eu.darken.sdmse.exclusion.core.ExclusionImporter.Container]: [version] gates the
 * whole format on import. The provenance fields ([appVersionName], [flavor], [androidRelease],
 * [deviceModel], …) don't block a restore — they drive the acknowledgement cards shown to the user,
 * since restoring across versions/devices/flavors is allowed but only "officially supported" between
 * identical SD Maid versions.
 *
 * The actual payload lives in sibling archive entries: `sections/<key>.json` per
 * [ConfigBackupContributor] and `databases/<key>` per [DatabaseBackupContributor].
 */
@Serializable
data class BackupEnvelope(
    val version: Int = VERSION,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val appVersionCode: Long,
    val appVersionName: String,
    val flavor: String,
    val androidSdkInt: Int,
    val androidRelease: String,
    val deviceManufacturer: String,
    val deviceModel: String,
) {
    companion object {
        const val VERSION = 1
    }
}
