package eu.darken.sdmse.common.clutter.dynamic.modules

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.clutter.dynamic.NestedPackageV2Matcher
import java.util.regex.Pattern
import javax.inject.Inject

@Reusable
class TencentMsflogsMarkerMatcher @Inject constructor() : NestedPackageV2Matcher(
    DataArea.Type.SDCARD,
    listOf("tencent", "msflogs"),
    setOf(Pattern.compile("^(?>tencent/msflogs/((?:\\w+/){2}\\w+))$", Pattern.CASE_INSENSITIVE)),
    emptySet(),
    emptySet(),
    Converter.PackagePathConverter()
) {
    override fun toString(): String = "TencentEncryptedLogsMarkerSource"

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun source(source: TencentMsflogsMarkerMatcher): MarkerSource
    }
}