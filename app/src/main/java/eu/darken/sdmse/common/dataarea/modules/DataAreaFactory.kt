package eu.darken.sdmse.common.dataarea.modules

import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.BuildConfigWrap.BuildType.RELEASE
import eu.darken.sdmse.common.dataarea.DataArea
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

class DataAreaFactory @Inject constructor(
    private val areaModules: Set<@JvmSuppressWildcards DataAreaModule>,
) {

    suspend fun build(): Collection<DataArea> {
        log(TAG) { "build()" }


        val firstPassResult = areaModules.map { it.firstPass() }.flatten()
        log(TAG) { "build(): First pass: $firstPassResult" }
        val secondPass = areaModules.map { it.secondPass(firstPassResult) }.flatten()
        log(TAG) { "build(): Second pass: $secondPass" }

        val uniqueAreas = secondPass.toSet()
        log(TAG) { "build(): Cleaned areas: $uniqueAreas" }

        // TODO only throw in beta builds
        if (BuildConfigWrap.BUILD_TYPE != RELEASE && secondPass.toSet().size != secondPass.size) {
            throw IllegalStateException("Duplicate data areas")
        }
        // TODO only throw in beta builds
        if (BuildConfigWrap.BUILD_TYPE != RELEASE && uniqueAreas.map { it.path }.toSet().size != uniqueAreas.size) {
            throw IllegalStateException("Duplicate data areas with overlapping paths")
        }

//        for (module in modules) {
//            val buildStorages: Collection<Storage> = module.build(locationListMap)
//            // Currently each module is only allowed to build one type of storage
//            for (storage in buildStorages) {
//                check(!locationListMap.containsKey(storage.getLocation())) {
//                    String.format(
//                        "Storage type %s is already build and added to %s",
//                        storage.getLocation(),
//                        locationListMap
//                    )
//                }
//            }
//
//            // if buildStorages is not empty we check for duplicates and add them
//            // get(LOCATION) will be null if buildStorages is empty and modules have to account for that
//            for (newStorage in buildStorages) {
//                var currentStorages: MutableCollection<Storage>? = locationListMap[newStorage.getLocation()]
//                if (currentStorages == null) currentStorages = HashSet<Storage>()
//                check(!currentStorages!!.contains(newStorage)) {
//                    String.format(
//                        "Duplicate storage: %s in %s",
//                        newStorage,
//                        currentStorages
//                    )
//                }
//                currentStorages.add(newStorage)
//                locationListMap[newStorage.getLocation()] = currentStorages
//            }
//        }
//        try {
//            if (shellIONormal != null) shellIONormal.close()
//        } catch (e: IOException) {
//            Timber.tag(TAG).w(e)
//        } finally {
//            shellIONormal = null
//        }
//        try {
//            if (shellIORoot != null) shellIORoot.close()
//        } catch (e: IOException) {
//            Timber.tag(TAG).w(e)
//        } finally {
//            shellIORoot = null
//        }
        return secondPass
    }

    companion object {
        private val TAG = logTag("DataArea", "Factory")
    }
}