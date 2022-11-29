//package eu.thedarken.sdmse.tools.storage.modules.privdata
//
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.FileOpsHelper
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.storage.*
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//import java.util.*
//
//internal class DataAppLibModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        if (!isRooted) return emptySet()
//        val dataStorages = storageMap[Location.DATA] ?: return emptySet()
//
//        StorageHelper.assertSpecificStorageLocation(dataStorages, Location.DATA)
//
//        return dataStorages.mapNotNull { dataStorage ->
//            if (!dataStorage.hasFlags(Storage.Flag.PRIMARY)) return@mapNotNull null
//            val file = JavaFile.build(dataStorage.file, "app-lib")
//            val mount: Mount = FileOpsHelper.findMount(mounts, file) ?: return@mapNotNull null
//
//            Storage(
//                    location = Location.APP_LIB,
//                    mount = mount,
//                    file = file
//            )
//        }
//    }
//}