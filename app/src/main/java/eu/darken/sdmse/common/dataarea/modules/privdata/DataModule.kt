//package eu.thedarken.sdmse.tools.storage.modules.privdata
//
//import android.os.Environment
//import eu.thedarken.sdmse.App
//import eu.thedarken.sdmse.tools.ApiHelper
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.FileOpsHelper
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.io.SDMFile
//import eu.thedarken.sdmse.tools.storage.Storage
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//import timber.log.Timber
//
//internal class DataModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        val results = mutableSetOf<Storage>()
//
//        val baseDataPath: SDMFile = JavaFile.build(Environment.getDataDirectory())
//        FileOpsHelper.findMount(mounts, baseDataPath)?.let { baseDataMount ->
//            Storage(
//                    location = Location.DATA,
//                    mount = baseDataMount,
//                    file = baseDataPath,
//                    flags = setOf(Storage.Flag.PRIMARY)
//            ).let { results.add(it) }
//        }
//
//        if (ApiHelper.hasMarshmallow()) {
//            try {
//                storageManagerOS.volumes?.forEach { volume ->
//                    if (!volume.isPrivate || volume.id?.startsWith("private:") != true || !volume.isMounted) {
//                        return@forEach
//                    }
//
//                    val extraDataMount = FileOpsHelper.findMount(mounts, volume.path) ?: return@forEach
//                    Storage(
//                            location = Location.DATA,
//                            mount = extraDataMount,
//                            file = JavaFile.build(volume.path),
//                            flags = setOf(Storage.Flag.SECONDARY)
//                    ).let { results.add(it) }
//                }
//            } catch (e: Exception) {
//                Timber.tag(TAG).e(e)
//            }
//        }
//
//        return results
//    }
//
//    companion object {
//        val TAG: String = App.logTag("Storage", "Module", "Data")
//    }
//}