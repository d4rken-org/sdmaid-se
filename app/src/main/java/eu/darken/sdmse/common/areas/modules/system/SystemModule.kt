package eu.darken.sdmse.common.areas.modules.system

import android.os.Environment
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.canRead
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.user.UserManager2
import java.io.File
import java.io.IOException
import javax.inject.Inject

@Reusable
class SystemModule @Inject constructor(
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    override suspend fun firstPass(): Collection<DataArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }


        val areas = mutableSetOf<DataArea>()

        run {
            DataArea(
                type = DataArea.Type.SYSTEM,
                path = LocalPath.build(Environment.getRootDirectory()),
                userHandle = userManager2.systemUser().handle,
                flags = setOf(DataArea.Flag.PRIMARY),
            ).run { areas.add(this) }
        }

        run {
            val originalPath = File("/vendor")
            val resolvedPath = try {
                originalPath.canonicalFile
            } catch (e: IOException) {
                log(TAG, ERROR) { "Failed to resolve canonical vendor path" }
                originalPath
            }
            val finalPath = LocalPath.build(resolvedPath)
            if (finalPath.canRead(gatewaySwitch)) {
                DataArea(
                    type = DataArea.Type.SYSTEM,
                    path = LocalPath.build(resolvedPath),
                    userHandle = userManager2.systemUser().handle,
                    flags = emptySet(),
                ).run { areas.add(this) }

            }
        }

        return areas
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SystemModule): DataAreaModule
    }

    companion object {
        val TAG = logTag("DataArea", "Module", "System")
    }
}