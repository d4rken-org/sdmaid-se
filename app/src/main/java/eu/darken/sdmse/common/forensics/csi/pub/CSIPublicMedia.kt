package eu.darken.sdmse.common.forensics.csi.pub

import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File

class CSIPublicMedia
//    : LocalCSIProcessor
{
//    fun hasJurisdiction(type: Location): Boolean {
//        return type === Location.PUBLIC_MEDIA
//    }
//
//    fun matchLocation(target: SDMFile): LocationInfo? {
//        var primary: Storage? = null
//        for (storage in getStorageManager().getStorages(Location.PUBLIC_MEDIA, true)) {
//            if (storage.hasFlags(Storage.Flag.PRIMARY)) primary = storage
//            val base: SDMFile = storage.getFile()
//            if (target.getPath().startsWith(base.getPath() + File.separator) && !target.getPath()
//                    .equals(base.getPath())
//            ) {
//                return LocationInfo(target, Location.PUBLIC_MEDIA, base.getPath() + File.separator, true, storage)
//            }
//        }
//        if (target.getPath().contains(ANDROID_MEDIA)) {
//            var prefix: String? = null
//            if (target.getPath().startsWith(LEGACY_PATH.path + File.separator) && !target.getPath()
//                    .equals(LEGACY_PATH.path)
//            ) {
//                prefix = LEGACY_PATH.path
//            }
//            if (prefix != null) {
//                return LocationInfo(target, Location.PUBLIC_MEDIA, prefix + ANDROID_MEDIA, true, primary)
//            }
//        }
//        return null
//    }
//
//    fun process(ownerInfo: OwnerInfo) {
//        val dirName: String = FileOpsHelper.getFirstDirName(ownerInfo.getLocationInfo().getPrefixFreePath())
//        if (isInstalled(dirName)) {
//            Timber.tag(TAG).v("%s is an installed package.", dirName)
//            ownerInfo.addOwner(Owner(dirName))
//            // There are no multiple owners for default dirs (dirname=pkgname).
//            return
//        }
//        val hiddenPkg = cleanDirName(dirName)
//        if (hiddenPkg != null && isInstalled(hiddenPkg)) {
//            Timber.tag(TAG).v("matched %s to %s.", dirName, hiddenPkg)
//            ownerInfo.addOwner(Owner(hiddenPkg))
//        }
//
//        // Once it's no longer a default folder name and match, we need to find all possible owners to protect against false positive corpses
//        val matches: Collection<Marker.Match> =
//            getClutterRepository().match(ownerInfo.getLocationInfo().getLocation(), dirName)
//        ownerInfo.addOwners(matches)
//
//        // Fallback, no downside to assuming that dirname=pkgname for PUBLIC_MEDIA if there are no other owners
//        if (ownerInfo.getOwners().isEmpty()) {
//            ownerInfo.addOwner(Owner(hiddenPkg ?: dirName))
//        }
//    }
//
//    private fun cleanDirName(currentName: String): String? {
//        return if (currentName.startsWith(".external.")) {
//            // rare modifier, seen it on my N5 with the .external.com.plexapp.android
//            currentName.substring(10)
//        } else if (currentName.startsWith("_") || currentName.startsWith(".")) {
//            // usually used in public/Android/data to hide stuff from uninstall
//            // some devs just don't know better
//            currentName.substring(1)
//        } else {
//            null
//        }
//    }

    companion object {
        val TAG: String = logTag("CSI", "MediaData")
        private val LEGACY_PATH = File("/storage/emulated/legacy/")
        val ANDROID_MEDIA = "/Android/media/".replace("/", File.separator)
    }
}