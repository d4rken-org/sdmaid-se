package eu.darken.sdmse.common.forensics.csi.pub

import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File

class CSIPublicObb
//    : LocalCSIProcessor
{

//    override suspend fun hasJurisdiction(type: DataArea.Type): Boolean = type == DataArea.Type.PUBLIC_OBB

//    fun matchLocation(file: SDMFile): LocationInfo? {
//        var primary: Storage? = null
//        for (storage in getStorageManager().getStorages(Location.PUBLIC_OBB, true)) {
//            if (storage.hasFlags(Storage.Flag.PRIMARY)) primary = storage
//            val base: SDMFile = storage.getFile()
//            if (file.getPath().startsWith(base.getPath() + File.separator) && !file.getPath().equals(base.getPath())) {
//                return LocationInfo(file, Location.PUBLIC_OBB, base.getPath() + File.separator, true, storage)
//            }
//        }
//        if (file.getPath().contains(PUBLIC_OBB)) {
//            var prefix: String? = null
//            if (file.getPath().startsWith(LEGACY_PATH.path + File.separator) && !file.getPath()
//                    .equals(LEGACY_PATH.path)
//            ) {
//                prefix = LEGACY_PATH.path
//            }
//            if (prefix != null) {
//                return LocationInfo(file, Location.PUBLIC_OBB, prefix + PUBLIC_OBB, true, primary)
//            }
//        }
//        return null
//    }
//
//    fun process(ownerInfo: OwnerInfo) {
//        val dirName: String = FileOpsHelper.getFirstDirName(ownerInfo.getLocationInfo().getPrefixFreePath())
//        if (isInstalled(dirName)) {
//            ownerInfo.addOwner(Owner(dirName))
//        } else {
//            val matches: Collection<Marker.Match> =
//                getClutterRepository().match(ownerInfo.getLocationInfo().getLocation(), dirName)
//            ownerInfo.addOwners(matches)
//        }
//        if (ownerInfo.getOwners().isEmpty()) {
//            val systemStorageManager = getContext().getSystemService(Context.STORAGE_SERVICE) as StorageManager
//            if (systemStorageManager == null) {
//                ownerInfo.setUnknownOwner(true)
//                return
//            }
//            val results: Array<File> = ownerInfo.getItem().getJavaFile().listFiles()
//            if (results != null) {
//                for (file in results) {
//                    val isMounted = systemStorageManager.isObbMounted(file.path)
//                    Timber.tag(TAG).v("obb mount check: %s -> %s", file.path, isMounted)
//                    // If its in use, its not a corpse
//                    if (isMounted) {
//                        ownerInfo.setUnknownOwner(true)
//                        break
//                    }
//                }
//            }
//        }
//    }

    companion object {
        val TAG: String = logTag("CSI", "PublicObb")
        private val LEGACY_PATH = File("/storage/emulated/legacy/")
        val PUBLIC_OBB = "/Android/obb/".replace("/", File.separator)
    }
}