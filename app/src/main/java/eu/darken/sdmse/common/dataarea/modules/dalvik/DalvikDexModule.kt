//package eu.thedarken.sdmse.tools.storage.modules.dalvik
//
//import android.os.Build
//import eu.thedarken.sdmse.App
//import eu.thedarken.sdmse.tools.ApiHelper
//import eu.thedarken.sdmse.tools.binaries.core.ArchHelper
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.FileOpsHelper
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.io.ReadTask.Builder.Companion.read
//import eu.thedarken.sdmse.tools.io.SDMFile
//import eu.thedarken.sdmse.tools.storage.Storage
//import eu.thedarken.sdmse.tools.storage.StorageHelper
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//import timber.log.Timber
//import java.io.IOException
//import java.util.*
//
//internal class DalvikDexModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//
//    private val architecture: ArchHelper.Type = if (Build.CPU_ABI.lowercase(Locale.ROOT).contains("x86")) {
//        ArchHelper.Type.X86
//    } else {
//        ArchHelper.Type.ARM
//    }
//    private val dalvikFolderName32bit: String = if (architecture == ArchHelper.Type.X86) "x86" else "arm"
//    private val dalvikFolderName64bit: String = if (architecture == ArchHelper.Type.X86) "x64" else "arm64"
//
//    private fun determinePossibleLocations(storageMap: Map<Location?, Collection<Storage>?>): MutableSet<SDMFile> {
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
//        return possibleLocation
//    }
//
//    override fun build(storageMap: Map<Location?, Collection<Storage>?>): Collection<Storage> {
//        if (!isRooted) return emptySet()
//        val shellIORoot = shellIORootOrNull ?: return emptySet()
//
//        val possibleLocation = determinePossibleLocations(storageMap)
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
//        val results = mutableSetOf<Storage>()
//        dalvikLocations.forEach { dalvikLocation ->
//            if (ApiHelper.hasLolliPop()) {
//                val bit32: SDMFile = JavaFile.build(dalvikLocation.javaFile, dalvikFolderName32bit)
//                FileOpsHelper.findMount(mounts, bit32)?.let {
//                    results.add(Storage(location = Location.DALVIK_DEX, mount = it, file = bit32))
//                }
//                val bit64: SDMFile = JavaFile.build(dalvikLocation.javaFile, dalvikFolderName64bit)
//                FileOpsHelper.findMount(mounts, bit64)?.let {
//                    results.add(Storage(location = Location.DALVIK_DEX, mount = it, file = bit64))
//                }
//            } else {
//                FileOpsHelper.findMount(mounts, dalvikLocation.javaFile)?.let {
//                    results.add(Storage(location = Location.DALVIK_DEX, mount = it, file = dalvikLocation))
//                }
//            }
//        }
//        return results
//    }
//
//    companion object {
//        val TAG: String = App.logTag("Storage", "Module", "DalvikDex")
//    }
//}