package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessChecker @Inject constructor() {

//    private val readAccessCacheMap: MutableMap<LocalPath, AccessType> = ConcurrentHashMap()
//    private val writeAccessCacheMap: MutableMap<LocalPath, AccessType> = ConcurrentHashMap()

//    val storageManager: StorageManagerSDM
//        get() = storageManagerLazy.get()
//    val appMap: Map<String, SDMPkgInfo>
//        get() = appRepo.getAppMap(AppsRequest.NON_STALE)


//    private fun LocationInfo.isScopedStorage(): Boolean {
//        if (!ApiHelper.hasAndroid11()) return false
//        if (!SCOPED_STORAGE_LOCATIONS.contains(location)) return false
//
//        // This includes data & obb
//        if (prefixFreePath == "Android") return true
//
//        if (prefixFreePath.startsWith("Android/data/") || prefixFreePath == "Android/data") return true
//        if (prefixFreePath.startsWith("Android/obb/") || prefixFreePath == "Android/obb") return true
//
//        // Storage root lookup
//        return prefixFreePath == SmartIO.ROOT_LOOKUP_TAG
//    }
//
//    private fun LocationInfo.hasSafAccess(checkWrite: Boolean): Boolean {
//        // SAF access is only reasonable for public locations
//        if (!location.isPublic) return false
//
//        if (dataArea?.safTreeUri == null) return false
//
//        // TODO check write access, for now we assume if read, write was granted too
//        // if(checkWrite && !storage?.documentsProviderUri!!.canWrite(context)) return false
//
//        return dataArea?.safTreeUri!!.canRead(context)
//    }
//
//    fun determineReadAccess(locationInfo: LocationInfo): AccessType {
//        val file = locationInfo.file
//
//        readAccessCacheMap[file]?.let { return it }
//
//        val location = locationInfo.location
//        val storage = locationInfo.dataArea
//        val isRooted = rootManager.rootContext.isRooted
//
//        return when {
//            storage == null -> {
//                if (isRooted) AccessType.ROOT else AccessType.NONE
//            }
//            ROOT_LOCATIONS.contains(location) && isRooted -> {
//                AccessType.ROOT
//            }
//            locationInfo.isScopedStorage() && isRooted -> {
//                AccessType.ROOT
//            }
//            locationInfo.isJavaReadable() -> {
//                AccessType.NORMAL
//            }
//            // Better performance to prefer root over SAF
//            isRooted -> {
//                AccessType.ROOT
//            }
//            locationInfo.hasSafAccess(checkWrite = false) -> {
//                AccessType.SAF
//            }
//            // Last resort
//            location.isPublic -> {
//                AccessType.NORMAL
//            }
//            else -> {
//                AccessType.NONE
//            }
//        }.also {
//            if (!BuildConfig.DEBUG) readAccessCacheMap[file] = it
//        }
//    }
//
//    fun determineWriteAccess(locationInfo: LocationInfo): LocalGateway.Mode? {
//        val file = locationInfo.file
//
//        writeAccessCacheMap[file]?.let { return it }
//
//        val storage = locationInfo.dataArea
//        val location = locationInfo.location
//        val isRooted = rootManager.rootContext.isRooted
//
//        return when {
//            storage == null -> {
//                if (isRooted) AccessType.ROOT else AccessType.NONE
//            }
//            // Don't check !PUBLIC_LOCATIONS.contains(location) because this is different from ROOT_LOCATIONS
//            ROOT_LOCATIONS.contains(location) && isRooted -> {
//                AccessType.ROOT
//            }
//            locationInfo.isScopedStorage() && isRooted -> {
//                AccessType.ROOT
//            }
//            // Primary public storage is writeable on all Android versions via Java with storage permissions, except Android/data|obb
//            // !ApiHelper.hasAndroid11() due to https://github.com/d4rken/sdmaid-public/issues/5179
//            !ApiHelper.hasAndroid11() && location.isPublic && storage.hasFlags(Storage.Flag.PRIMARY) -> {
//                AccessType.NORMAL
//            }
//            // Restrict access to these, out of SD Maids scope to handle these correctly
////            BLOCKED_FILESYSTEMS.contains(storage.mount.fileSystemType) -> {
////                AccessType.NONE
////            }
//            locationInfo.isJavaWriteable() -> {
//                AccessType.NORMAL
//            }
//            locationInfo.hasSafAccess(checkWrite = true) -> {
//                AccessType.SAF
//            }
//            isRooted -> {
//                AccessType.ROOT
//            }
//            // Last resort
//            location.isPublic -> {
//                AccessType.NORMAL
//            }
//            else -> {
//                AccessType.NONE
//            }
//        }.also {
//            if (!BuildConfig.DEBUG) writeAccessCacheMap[file] = it
//        }
//    }
//
//    private fun LocationInfo.isJavaReadable(): Boolean {
//        if (file.javaFile.canRead()) return true
//        // If we can list files in the parent, assume we can read the child
//        // Only edgecase would be Android/data on Android 11+, this has to be checked beforehand.
//        if (file.parentFile?.javaFile?.listFiles() != null) return true
//
//        return false
//    }
//
//    private fun LocationInfo.isJavaWriteable(): Boolean {
//        var father: LocalPath? = this.file.parentFile ?: return false
//        while (!father!!.isDirectory && father.parent != null && father.path != this.prefix) {
//            father = father.parentFile
//            if (father == null) return false
//        }
//        val testName = "sdm_write_test-" + randomString
//        val testFile = File(father.javaFile, testName)
//        val writable: Boolean = try {
//            // Moto G3 usb otg allowed mkdir but not file creation
//            testFile.createNewFile()
//        } catch (ignore: IOException) {
//            false
//        }
//        Timber.tag(TAG).v("Write test %b for %s", writable, testFile)
//        var deletable = false
//        if (writable) {
//            deletable = !testFile.exists() || testFile.delete()
//            if (!deletable && testFile.exists()) {
//                Timber.tag(TAG).e("Can't delete test file:%s", testFile)
//                testFile.deleteOnExit()
//            }
//        }
//        return writable && deletable
//    }

    companion object {
        private val TAG = logTag("Gateway", "AccessCheck")
        private val ROOT_LOCATIONS = setOf(
            // https://github.com/d4rken/sdmaid-public/issues/413
            // Apps on some devices have 755 on parent dirs in /data/data, but children don't.
            DataArea.Type.PRIVATE_DATA,  // Everything in /data should default to root access
            DataArea.Type.DATA,  // The /oat folders are system read only
            DataArea.Type.APP_APP
        )

        private val SCOPED_STORAGE_LOCATIONS = listOf(
            DataArea.Type.SDCARD,
            DataArea.Type.PUBLIC_DATA,
            DataArea.Type.PUBLIC_OBB
        )

//        private val BLOCKED_FILESYSTEMS: Collection<FileSystem> = listOf(
//            FileSystem.PROC,
//            FileSystem.DEBUGFS,
//            FileSystem.SYSFS,
//            FileSystem.DEVPTS
//        )
    }
}