package eu.darken.sdmse.appcontrol.core.toggle

import dagger.Reusable
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Inject

@Reusable
class ComponentToggler @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val pkgOps: PkgOps,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    suspend fun changePackageState(installId: Installed.InstallId, enabled: Boolean) {
        log(TAG, INFO) { "changePackageState($installId,$enabled)" }
        adoptChildResource(pkgOps)
        pkgOps.changePackageState(installId.pkgId, enabled)
    }

    companion object {
        private val TAG = logTag("AppControl", "ComponentToggler")
    }

}