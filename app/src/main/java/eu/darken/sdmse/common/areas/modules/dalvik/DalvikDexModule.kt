package eu.darken.sdmse.common.areas.modules.dalvik

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.Architecture
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.hasFlags
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.canRead
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.user.UserManager2
import java.util.*
import javax.inject.Inject

@Reusable
class DalvikDexModule @Inject constructor(
    private val architecture: Architecture,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        val possibleLocation = mutableSetOf<LocalPath>()

        firstPass
            .filter { it.type == DataArea.Type.DATA }
            .filter { it.hasFlags(DataArea.Flag.PRIMARY) }
            .map { LocalPath.build(it.path as LocalPath, "dalvik-cache") }
            .run { possibleLocation.addAll(this) }

        firstPass
            .filter { it.type == DataArea.Type.DOWNLOAD_CACHE }
            .map { LocalPath.build(it.path as LocalPath, "dalvik-cache") }
            .run { possibleLocation.addAll(this) }

        return possibleLocation
            .map { basePath -> architecture.folderNames.map { LocalPath.build(basePath, it) } }
            .flatten()
            .onEach { log(TAG, VERBOSE) { "Checking $it" } }
            .filter { gateway.exists(it, mode = LocalGateway.Mode.ROOT) }
            .map {
                DataArea(
                    type = DataArea.Type.DALVIK_DEX,
                    path = it,
                    userHandle = userManager2.systemUser().handle,
                )
            }
            .filter {
                val canRead = it.path.canRead(gatewaySwitch)
                if (!canRead) log(TAG) { "Can't read $it" }
                canRead
            }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DalvikDexModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "DalvikDex")
    }
}