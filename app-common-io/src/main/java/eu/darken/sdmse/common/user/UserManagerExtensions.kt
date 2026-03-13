package eu.darken.sdmse.common.user

import android.os.UserHandle
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserManager2.Companion.TAG


suspend fun UserManager2.ourInstall() = InstallId(
    pkgId = BuildConfigWrap.APPLICATION_ID.toPkgId(),
    userHandle = currentUser().handle
)


internal fun UserHandle.getIdentifier(): Int? {
    return try {
        val getIdentifier = this.javaClass.getMethod("getIdentifier")
        getIdentifier.invoke(this) as Int
    } catch (e: Exception) {
        log(TAG, WARN) { "UserHandle.getIdentifier(): Failed to use reflective access on getIdentifier: ${e.asLog()} " }
        null
    }
}