//package eu.thedarken.sdmse.tools.storage.modules.privdata
//
//import eu.thedarken.sdmse.App
//import eu.thedarken.sdmse.tools.ApiHelper
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
//import java.util.regex.Pattern
//
//internal class PrivateDataModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        if (!isRooted) return emptySet()
//
//        return if (ApiHelper.hasAndroid11() && !mountMaster.shouldUseMountMaster()) {
//            Timber.tag(TAG).w("We are on Android 11 and mount-master is unavailable :(")
//            getApi30Plus(storageMap)
//        } else {
//            determineLegacy(storageMap)
//        }
//    }
//
//    /**
//     * Since API30 (Android 11), /data/data is just a bind mount to hide other apps
//     * https://android.googlesource.com/platform//system/core/+/3cca270e95ca8d8bc8b800e2b5d7da1825fd7100
//     * Looking at /data/data will just show our own package, even with root
//     * /data_mirror/data_ce/null/0
//     * /data_mirror/data_de/null/0
//     */
//    private fun getApi30Plus(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        val dataStorages = storageMap[Location.DATA] ?: return emptySet()
//
//        StorageHelper.assertSpecificStorageLocation(dataStorages, Location.DATA)
//
//        val results = mutableSetOf<Storage>()
//
//        val privateDataFolders = listOf("data_ce", "data_de")
//
//        val mirrorStorage = JavaFile.build("/data_mirror")
//
//        privateDataFolders.forEach { folder ->
//            multiUser.userInfos.forEach inner@{ userInfo ->
//                val userDir = JavaFile.build(mirrorStorage, "$folder/null/${userInfo.userId}")
//
//                val mount = FileOpsHelper.findMount(mounts, userDir) ?: return@inner
//
//                Storage(
//                    location = Location.PRIVATE_DATA,
//                    mount = mount,
//                    file = userDir,
//                    userHandle = userInfo.userId.toLong(),
//                    flags = setOf(Storage.Flag.PRIMARY)
//                ).run { results.add(this) }
//            }
//        }
//
//        return results
//    }
//
//    /**
//     * API 29 (Android 9) and lower
//     */
//    private fun getPreApi30(base: SDMFile): Collection<SDMFile> = try {
//        val privDirCanidates = mutableSetOf<SDMFile>()
//
//        shellIORootOrNull
//            ?.let {
//                val readTaskUser = read(JavaFile.build(base, "user")).atLevelContent().build()
//                it.read(readTaskUser).files
//            }
//            ?.let {
//                privDirCanidates.addAll(it)
//            }
//
//
//        if (ApiHelper.hasAndroidN()) {
//            shellIORootOrNull
//                ?.let {
//                    val readTaskLocale = read(JavaFile.build(base, "user_de")).atLevelContent().build()
//                    it.read(readTaskLocale).files
//                }
//                ?.let {
//                    privDirCanidates.addAll(it)
//                }
//        }
//
//        privDirCanidates.filter { USER_DIR_NUMBER_PATTERN.matcher(it.name).matches() }
//    } catch (e: IOException) {
//        Timber.tag(TAG).e(e)
//        emptySet()
//    }
//
//    // Pre Android 11, pre /data_mirror
//    private fun determineLegacy(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        val dataStorages = storageMap[Location.DATA] ?: return emptySet()
//
//        StorageHelper.assertSpecificStorageLocation(dataStorages, Location.DATA)
//
//        val results = mutableSetOf<Storage>()
//
//        for (baseStorage in dataStorages) {
//            val base = baseStorage.file
//            val userDirs: Collection<SDMFile> = getPreApi30(base)
//
//            if (userDirs.isNotEmpty()) {
//                for (userDir in userDirs) {
//                    val mount = FileOpsHelper.findMount(mounts, userDir)
//                    if (mount != null) {
//                        Storage(
//                            location = Location.PRIVATE_DATA,
//                            mount = mount,
//                            file = userDir,
//                            userHandle = java.lang.Long.valueOf(userDir.name),
//                            flags = baseStorage.flags
//                        ).let { results.add(it) }
//                    }
//                }
//            } else if (baseStorage.hasFlags(Storage.Flag.PRIMARY) && !multiUser.supportsMultipleUsers()) {
//                val simple: SDMFile = JavaFile.build(base, "data")
//                FileOpsHelper.findMount(mounts, simple)?.let {
//                    val storage = Storage(
//                        location = Location.PRIVATE_DATA,
//                        mount = it,
//                        file = simple,
//                        userHandle = multiUser.currentUserHandle.toLong(),
//                        flags = setOf(Storage.Flag.PRIMARY)
//                    )
//                    results.add(storage)
//                }
//            }
//        }
//
//        shellIORootOrNull
//            ?.let {
//                try {
//                    val readTask = read(JavaFile.build("/dbdata/databases/")).atLevelItem().build()
//                    it.read(readTask).files.singleOrNull()
//                } catch (e: IOException) {
//                    Timber.tag(TAG).e(e)
//                    null
//                }
//            }
//            ?.let { dbdata ->
//                FileOpsHelper.findMount(mounts, dbdata)?.let {
//                    val storage = Storage(
//                        location = Location.PRIVATE_DATA,
//                        mount = it,
//                        file = dbdata
//                    )
//                    results.add(storage)
//                }
//            }
//
//        shellIORootOrNull
//            ?.let {
//                try {
//                    val readTask = read(JavaFile.build("/datadata/")).atLevelItem().build()
//                    it.read(readTask).files.singleOrNull()
//                } catch (e: IOException) {
//                    Timber.tag(TAG).e(e)
//                    null
//                }
//            }
//            ?.let { datadata ->
//                FileOpsHelper.findMount(mounts, datadata)?.let {
//                    val storage = Storage(
//                        location = Location.PRIVATE_DATA,
//                        mount = it,
//                        file = datadata
//                    )
//                    results.add(storage)
//                }
//            }
//
//        return results
//    }
//
//    companion object {
//        val TAG: String = App.logTag("Storage", "Module", "PrivateData")
//        private val USER_DIR_NUMBER_PATTERN = Pattern.compile("([0-9]{1,2})")
//    }
//}