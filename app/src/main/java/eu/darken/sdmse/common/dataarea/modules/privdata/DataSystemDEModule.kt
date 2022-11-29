//package eu.thedarken.sdmse.tools.storage.modules.privdata
//
//import eu.thedarken.sdmse.tools.ApiHelper
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.FileOpsHelper
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.io.SDMFile
//import eu.thedarken.sdmse.tools.storage.Storage
//import eu.thedarken.sdmse.tools.storage.StorageHelper
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//
//internal class DataSystemDEModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        if (!ApiHelper.hasAndroidN()) return emptySet()
//        if (!isRooted) return emptySet()
//        val dataStorages = storageMap[Location.DATA] ?: return emptySet()
//
//        StorageHelper.assertSpecificStorageLocation(dataStorages, Location.DATA)
//
//        val results = mutableSetOf<Storage>()
//        for (dataStorage in dataStorages) {
//            if (!dataStorage.hasFlags(Storage.Flag.PRIMARY)) continue
//            for (userInfo in multiUser.userInfos) {
//                val file: SDMFile = JavaFile.build(
//                        dataStorage.file,
//                        "system_de", userInfo.userId.toString())
//                val mount = FileOpsHelper.findMount(mounts, file) ?: continue
//
//                Storage(
//                        location = Location.DATA_SYSTEM_DE,
//                        mount = mount,
//                        file = file,
//                        flags = dataStorage.flags,
//                        userHandle = userInfo.userId.toLong(),
//                ).let { results.add(it) }
//            }
//        }
//        return results
//    }
//}