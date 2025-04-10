package eu.darken.sdmse.common.pkgs.features

import eu.darken.sdmse.common.user.UserHandle2

interface Installed : PkgInfo {

    val userHandle: UserHandle2

    val installId: InstallId
        get() = InstallId(id, userHandle)

    /**
     * Has a few false positives, e.g.
     * com.android.cts.priv.ctsshim
     * com.android.nfc
     * com.google.android.devicelockcontroller
     * com.google.android.microdroid.empty_payload
     * com.google.android.virtualmachine.res
     * com.google.android.compos.payload
     * com.android.cts.ctsshim
     */
    val hasNoSettings: Boolean
        get() {
            val isMainline = packageName.startsWith("com.google.mainline.")
            if (isMainline) return true
            val hasApexSource = applicationInfo?.sourceDir?.startsWith("/apex/") == true
            if (hasApexSource) return true
            val hasApexLibrary = applicationInfo?.nativeLibraryDir?.startsWith("/apex/") == true
            return hasApexLibrary
        }
}