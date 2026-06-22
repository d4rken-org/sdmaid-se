package eu.darken.sdmse.common.backup

import eu.darken.sdmse.common.serialization.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Top-level container for a config backup file.
 *
 * Versioned like [eu.darken.sdmse.exclusion.core.ExclusionImporter.Container]: [version] gates the
 * whole format on import. The provenance fields ([appVersionName], [flavor], [androidRelease],
 * [deviceModel], …) are not used to block a restore — they drive the acknowledgement cards shown to
 * the user, since restoring across versions/devices/flavors is allowed but only "officially supported"
 * between identical SD Maid versions.
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
    /** Section key (a [ConfigBackupContributor.key]) → that contributor's opaque payload. */
    val sections: Map<String, JsonElement>,
) {
    companion object {
        const val VERSION = 1
    }
}
