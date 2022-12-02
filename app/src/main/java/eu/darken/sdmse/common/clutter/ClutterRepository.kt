package eu.darken.sdmse.common.clutter

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.storageareas.StorageArea
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClutterRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val markerSources: Set<@JvmSuppressWildcards MarkerSource>,
) : MarkerSource {


    init {
//        if (BuildConfig.DEBUG) markerSources.add(DebugMarkerSource(context, appRepo))
//        markerSources.add(ProductionMarkerSource(context, appRepo))
//        markerSources.add(EveryplayMarkerMatcher())
//        markerSources.add(UTSystemConfigMarkerMatcher())
//        markerSources.add(IQQIMarkerMatcher())
//        markerSources.add(PrivateToSdcardPathDevMistakeMarker())
//        markerSources.add(PrivateToSdcardPathDevMistakeMarker2())
//        markerSources.add(VideoCacheMarkerMatcher())
//        markerSources.add(BmwGroupMarkerMatcher())
//        markerSources.add(HelpshiftDynamicMarker())
//        markerSources.add(TencentEncryptedLogsMarkerMatcher())
//        markerSources.add(TencentMsflogsMarkerMatcher())
//        markerSources.add(TencentTbsBackupDynamicMarker())
//        Timber.tag(TAG).d("Loaded %d marker sources", markerSources.size)
//        for (s in markerSources) Timber.tag(TAG).d("Loaded: %s", s)
    }

    override suspend fun getMarkerForPackageName(packageName: String): Collection<Marker> {
        val result: MutableCollection<Marker> = ArrayList()
        for (markerSource in markerSources) result.addAll(markerSource.getMarkerForPackageName(packageName))
        return result
    }

    override suspend fun match(areaType: StorageArea.Type, prefixFreeBasePath: String): Collection<Marker.Match> {
        val result: MutableCollection<Marker.Match> = ArrayList()
        for (markerSource in markerSources) result.addAll(markerSource.match(areaType, prefixFreeBasePath))
        return result
    }

    override suspend fun getMarkerForLocation(areaType: StorageArea.Type): Collection<Marker> {
        val result: MutableCollection<Marker> = ArrayList()
        for (markerSource in markerSources) result.addAll(markerSource.getMarkerForLocation(areaType))
        return result
    }

    companion object {
        val TAG: String = logTag("ClutterRepository")
    }
}