package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.exists
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import javax.inject.Inject

@Reusable
class ExistCheck @Inject constructor(
    private val gatewaySwitch: GatewaySwitch
) : DalvikCheck {

    suspend fun check(candidates: Collection<LocalPath>): DalvikCheck.Result {
        return DalvikCheck.Result(
            hasKnownUnknownOwner = candidates.any {
                it.exists(gatewaySwitch)
            }
        )
    }
}