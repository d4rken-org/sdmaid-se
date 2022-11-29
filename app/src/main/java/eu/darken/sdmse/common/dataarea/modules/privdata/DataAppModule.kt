//package eu.thedarken.sdmse.tools.storage.modules.privdata
//
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.storage.Storage
//import eu.thedarken.sdmse.tools.storage.StorageHelper
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//
//internal class DataAppModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        if (!isRooted) return emptySet()
//        val dataStorages = storageMap[Location.DATA] ?: return emptySet()
//
//        StorageHelper.assertSpecificStorageLocation(dataStorages, Location.DATA)
//
//        return dataStorages.map { dataStorage ->
//            Storage(
//                    location = Location.APP_APP,
//                    mount = dataStorage.mount,
//                    file = JavaFile.build(dataStorage.file, "app"),
//                    flags = dataStorage.flags
//            )
//        }
//    }
//}