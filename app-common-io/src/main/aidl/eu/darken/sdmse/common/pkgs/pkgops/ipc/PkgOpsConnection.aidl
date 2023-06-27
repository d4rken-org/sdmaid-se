package eu.darken.sdmse.common.pkgs.pkgops.ipc;

import android.content.pm.PackageInfo;
import eu.darken.sdmse.common.ipc.RemoteInputStream;

interface PkgOpsConnection {

    String getUserNameForUID(int uid);

    String getGroupNameforGID(int gid);

    boolean forceStop(String packageName);

    boolean clearCache(String packageName, int handleId);

    boolean trimCaches(long desiredBytes, String storageId);

    List<PackageInfo> getInstalledPackagesAsUser(int flags, int handleId);

    RemoteInputStream getInstalledPackagesAsUserStream(int flags, int handleId);

    void setApplicationEnabledSetting (String packageName, int newState, int flags);

}