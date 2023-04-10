package eu.darken.sdmse.common.pkgs.pkgops.root;

import android.content.pm.PackageInfo;
import eu.darken.sdmse.common.root.io.RemoteInputStream;

interface PkgOpsConnection {

    String getUserNameForUID(int uid);

    String getGroupNameforGID(int gid);

    boolean forceStop(String packageName);

    List<PackageInfo> getInstalledPackagesAsUser(int flags, int handleId);

    RemoteInputStream getInstalledPackagesAsUserStream(int flags, int handleId);

    void setApplicationEnabledSetting (String packageName, int newState, int flags);

}