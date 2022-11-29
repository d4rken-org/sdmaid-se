//package eu.thedarken.sdmse.tools.storage.modules.dalvik
//
//import eu.thedarken.sdmse.App
//import eu.thedarken.sdmse.tools.ApiHelper
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.FileOpsHelper
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.io.ReadTask.Builder.Companion.read
//import eu.thedarken.sdmse.tools.io.SDMFile
//import eu.thedarken.sdmse.tools.storage.*
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//import timber.log.Timber
//import java.io.IOException
//
//internal class DalvikProfileModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//
//    override fun build(storageMap: Map<Location?, Collection<Storage>?>): Collection<Storage> {
//        if (!ApiHelper.hasLolliPop()) return emptySet()
//        if (!isRooted) return emptySet()
//        val shellIORoot = shellIORootOrNull ?: return emptySet()
//
//        val possibleLocation = mutableSetOf<SDMFile>()
//
//        storageMap[Location.DATA]?.let { dataStorages ->
//            StorageHelper.assertSpecificStorageLocation(dataStorages, Location.DATA)
//            for (dataStorage in dataStorages) {
//                if (!dataStorage.hasFlags(Storage.Flag.PRIMARY)) continue
//                possibleLocation.add(JavaFile.build(dataStorage.file, "dalvik-cache"))
//            }
//        }
//
//        storageMap[Location.DOWNLOAD_CACHE]?.let { cacheStorages ->
//            StorageHelper.assertSpecificStorageLocation(cacheStorages, Location.DOWNLOAD_CACHE)
//            for ((file) in cacheStorages) {
//                possibleLocation.add(JavaFile.build(file, "dalvik-cache"))
//            }
//        }
//
//        if (possibleLocation.isEmpty()) return emptySet()
//
//        val dalvikLocations = try {
//            val readTask = read(possibleLocation).atLevelItem().build()
//            shellIORoot.read(readTask).files
//        } catch (e: IOException) {
//            Timber.tag(TAG).e(e)
//            emptySet()
//        }
//
//        return dalvikLocations.mapNotNull { dalvikLocation ->
//            val profiles: SDMFile = JavaFile.build(dalvikLocation, "profiles")
//            val mountProfiles: Mount = FileOpsHelper.findMount(mounts, profiles) ?: return@mapNotNull null
//
//            Storage(
//                    location = Location.DALVIK_PROFILE,
//                    mount = mountProfiles,
//                    file = profiles
//            )
//        }
//    }
//
//    companion object {
//        val TAG: String = App.logTag("Storage", "Module", "DalvikProfile")
//    }
//}