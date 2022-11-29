//package eu.thedarken.sdmse.tools.storage.modules.system
//
//import eu.thedarken.sdmse.App
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.FileOpsHelper
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.storage.Mount
//import eu.thedarken.sdmse.tools.storage.Storage
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//import timber.log.Timber
//import java.io.File
//import java.io.IOException
//
///**
// * https://github.com/d4rken/sdmaid-public/issues/441
// */
//internal class OemModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        val originalPath = File("/oem")
//        val resolvedPath: File = try {
//            originalPath.canonicalFile
//        } catch (e: IOException) {
//            Timber.tag(TAG).e(e)
//            return emptySet()
//        }
//
//        val mount: Mount = if (originalPath == resolvedPath) {
//            // Not a symlink
//            FileOpsHelper.findMount(mounts, originalPath)
//        } else {
//            // Symlink (e.g. oem -> /system/oem)
//            // If we want to remount this we need to remount the symlink target
//            FileOpsHelper.findMount(mounts, resolvedPath)
//        } ?: return emptySet()
//
//        val storage = Storage(
//                location = Location.OEM,
//                mount = mount,
//                file = JavaFile.build(originalPath)
//        )
//        return setOf(storage)
//    }
//
//    companion object {
//        val TAG = App.logTag("Storage", "Module", "Oem")
//    }
//}