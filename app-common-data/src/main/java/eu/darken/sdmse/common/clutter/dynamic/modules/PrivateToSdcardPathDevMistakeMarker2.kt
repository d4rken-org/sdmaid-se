package eu.darken.sdmse.common.clutter.dynamic.modules

import android.annotation.SuppressLint
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
 * /sdcard/data/user/0/com.some.pkg
 */
@SuppressLint("SdCardPath")
@Reusable
class PrivateToSdcardPathDevMistakeMarker2 @Inject constructor() : NestedPackageMatcher(
    DataArea.Type.SDCARD,
    listOf("data", "user", "0"),
    emptySet()
) {
    override fun toString(): String = "PrivateToSdcardPathDevMistakeMarker2"

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun source(source: PrivateToSdcardPathDevMistakeMarker2): MarkerSource
    }
}