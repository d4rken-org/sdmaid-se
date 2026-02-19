package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.local.LocalPath
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