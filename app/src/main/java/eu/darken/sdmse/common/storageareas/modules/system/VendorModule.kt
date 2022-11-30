package eu.darken.sdmse.common.storageareas.modules.system

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.storageareas.StorageArea
import eu.darken.sdmse.common.storageareas.modules.DataAreaModule
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject

/**
 * https://github.com/d4rken/sdmaid-public/issues/410
 */
@Reusable
class VendorModule @Inject constructor(
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    override suspend fun firstPass(): Collection<StorageArea> = emptySet()

    override suspend fun secondPass(firstPass: Collection<StorageArea>): Collection<StorageArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        val basePath = LocalPath.build("/vendor")

        if (!gateway.exists(basePath, mode = LocalGateway.Mode.ROOT)) {
            log(TAG, INFO) { "Doesn't exist: $basePath" }
            return emptySet()
        }

        return setOf(
            StorageArea(
                type = StorageArea.Type.VENDOR,
                path = basePath,
                userHandle = userManager2.systemUser,
            )
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: VendorModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("StorageArea", "Module", "Vendor")
    }
}