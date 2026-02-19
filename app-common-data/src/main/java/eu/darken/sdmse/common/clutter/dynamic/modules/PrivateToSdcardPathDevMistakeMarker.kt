package eu.darken.sdmse.common.clutter.dynamic.modules

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.clutter.dynamic.NestedPackageMatcher
import javax.inject.Inject

/**
 * /sdcard/data/data/com.some.pkg
 */
@Reusable
class PrivateToSdcardPathDevMistakeMarker @Inject constructor() : NestedPackageMatcher(
    DataArea.Type.SDCARD,
    listOf("data", "data"),
    emptySet()
) {
    override fun toString(): String = "PrivateToSdcardPathDevMistakeMarker"

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun source(source: PrivateToSdcardPathDevMistakeMarker): MarkerSource
    }
}