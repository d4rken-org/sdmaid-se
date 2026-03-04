package eu.darken.sdmse.common.forensics.csi.source.tools

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.csi.source.AppSourceCheck
import eu.darken.sdmse.common.forensics.csi.toOwners
import javax.inject.Inject

@Reusable
class AppSourceClutterCheck @Inject constructor(
    private val clutterRepo: ClutterRepo,
) : AppSourceCheck {

    override suspend fun process(areaInfo: AreaInfo): AppSourceCheck.Result {
        val dirName: String = areaInfo.prefixFreeSegments.first()
        val matches = clutterRepo.match(areaInfo.type, listOf(dirName))
        val owners = matches.map { it.toOwners(areaInfo) }.flatten().toSet()
        return AppSourceCheck.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AppSourceClutterCheck): AppSourceCheck
    }
}