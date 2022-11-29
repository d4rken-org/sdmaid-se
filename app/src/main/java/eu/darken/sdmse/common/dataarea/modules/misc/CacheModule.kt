//package eu.thedarken.sdmse.tools.storage.modules.misc
//
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.FileOpsHelper
//import eu.thedarken.sdmse.tools.storage.Storage
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//import eu.thedarken.sdmse.tools.storage.modules.StorageModuleHelper
//
//internal class CacheModule(storageModuleHelper: StorageModuleHelper?) : StorageFactoryModule(storageModuleHelper) {
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        val file = environment.downloadCacheDirectory
//        val mount = FileOpsHelper.findMount(mounts, file) ?: return emptySet()
//
//        return Storage(
//                location = Location.DOWNLOAD_CACHE,
//                mount = mount,
//                file = file
//        ).let { setOf(it) }
//    }
//}