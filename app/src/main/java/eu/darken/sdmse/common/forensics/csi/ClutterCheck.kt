package eu.darken.sdmse.common.forensics.csi

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.getFirstDirElement
import javax.inject.Inject

@Reusable
class ClutterCheck @Inject constructor(
    private val clutterRepo: ClutterRepo,
) : CSISubProcessor {

    override suspend fun process(areaInfo: AreaInfo): CSISubProcessor.Result {
        val dirName: String = areaInfo.prefixFreePath.getFirstDirElement()
        val matches = clutterRepo.match(areaInfo.type, dirName)
        val owners = matches.map { it.toOwners() }.flatten().toSet()
        return CSISubProcessor.Result(owners)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ClutterCheck): CSISubProcessor
    }
}