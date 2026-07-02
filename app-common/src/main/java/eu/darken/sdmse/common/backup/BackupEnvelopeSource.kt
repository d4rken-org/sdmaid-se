package eu.darken.sdmse.common.backup

import android.os.Build
import eu.darken.sdmse.common.BuildConfigWrap
import java.time.Instant
import javax.inject.Inject

/**
 * Creates the provenance [BackupEnvelope] for new backup archives. Separate from
 * [ConfigBackupManager] so JVM tests can stub it — [BuildConfigWrap] can't initialize outside an
 * Android build.
 */
class BackupEnvelopeSource @Inject constructor() {
    fun create(): BackupEnvelope = BackupEnvelope(
        createdAt = Instant.now(),
        appVersionCode = BuildConfigWrap.VERSION_CODE,
        appVersionName = BuildConfigWrap.VERSION_NAME,
        flavor = BuildConfigWrap.FLAVOR.name,
        androidSdkInt = Build.VERSION.SDK_INT,
        androidRelease = Build.VERSION.RELEASE ?: "?",
        deviceManufacturer = Build.MANUFACTURER ?: "?",
        deviceModel = Build.MODEL ?: "?",
    )
}
