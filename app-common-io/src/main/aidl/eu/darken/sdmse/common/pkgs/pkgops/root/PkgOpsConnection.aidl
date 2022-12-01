package eu.darken.sdmse.common.pkgs.pkgops.root;

import android.content.pm.PackageInfo;

interface PkgOpsConnection {

    String getUserNameForUID(int uid);

    String getGroupNameforGID(int gid);

    boolean forceStop(String packageName);

    List<PackageInfo> getInstalledPackagesAsUser(int flags, int userId);

}