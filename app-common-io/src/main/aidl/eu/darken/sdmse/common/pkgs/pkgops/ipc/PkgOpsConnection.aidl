package eu.darken.sdmse.common.pkgs.pkgops.ipc;

import android.content.pm.PackageInfo;
import eu.darken.sdmse.common.ipc.RemoteInputStream;
import eu.darken.sdmse.common.pkgs.features.InstallId;
import eu.darken.sdmse.common.pkgs.pkgops.ipc.RunningPackagesResult;

interface PkgOpsConnection {

    String getUserNameForUID(int uid);

    String getGroupNameforGID(int gid);

    RunningPackagesResult getRunningPackages();

    boolean forceStop(String packageName);

    boolean clearCacheAsUser(String packageName, int handleId, boolean dryRun);

    boolean clearCache(String packageName, boolean dryRun);

    boolean trimCaches(long desiredBytes, String storageId, boolean dryRun);

    List<PackageInfo> getInstalledPackagesAsUser(long flags, int handleId);

    RemoteInputStream getInstalledPackagesAsUserStream(long flags, int handleId);

    void setApplicationEnabledSetting (String packageName, int newState, int flags);

    boolean grantPermission(String packageName, int handleId, String permissionId);

    boolean revokePermission(String packageName, int handleId, String permissionId);

    boolean setAppOps(String packageName, int handleId, String key, String value);
}