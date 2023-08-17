package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import eu.darken.sdmse.common.forensics.csi.toOwners
import javax.inject.Inject

@Reusable
class DalvikClutterCheck @Inject constructor(
    private val clutterRepo: ClutterRepo,
) : DalvikCheck {

    suspend fun process(areaInfo: AreaInfo): DalvikCheck.Result {
        val dirName: String = areaInfo.prefixFreeSegments.first()
        val matches = clutterRepo.match(areaInfo.type, listOf(dirName))
        val owners = matches.map { it.toOwners(areaInfo) }.flatten().toSet()
        return DalvikCheck.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: DalvikClutterCheck): DalvikCheck
    }
}