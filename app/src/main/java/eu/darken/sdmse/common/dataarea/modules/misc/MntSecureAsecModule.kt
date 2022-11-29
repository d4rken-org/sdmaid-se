//package eu.thedarken.sdmse.tools.storage.modules.misc
//
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.FileOpsHelper
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.io.SDMFile
//import eu.thedarken.sdmse.tools.storage.Storage
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//
//internal class MntSecureAsecModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        if (!isRooted) return emptySet()
//
//        val file: SDMFile = JavaFile.absolute("mnt", "secure", "asec")
//        val mount = FileOpsHelper.findMount(mounts, file) ?: return emptySet()
//
//        return Storage(
//                location = Location.MNT_SECURE_ASEC,
//                mount = mount,
//                file = file
//        ).let { setOf(it) }
//    }
//}