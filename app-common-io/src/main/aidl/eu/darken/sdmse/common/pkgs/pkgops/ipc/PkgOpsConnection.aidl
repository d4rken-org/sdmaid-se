package eu.darken.sdmse.common.pkgs.pkgops.ipc;

import android.content.pm.PackageInfo;
import eu.darken.sdmse.common.ipc.RemoteInputStream;
import eu.darken.sdmse.common.pkgs.features.InstallId;
import eu.darken.sdmse.common.pkgs.pkgops.ipc.RunningPackagesResult;

interface PkgOpsConnection {

    RunningPackagesResult getRunningPackages();

    boolean forceStop(in InstallId installId);

    boolean clearCacheAsUser(String packageName, int handleId, boolean dryRun);

    boolean clearCache(String packageName, boolean dryRun);

    boolean trimCaches(long desiredBytes, String storageId, boolean dryRun);

    PackageInfo getPackageInfoAsUser(String packageName, long flags, int handleId);

    List<PackageInfo> getInstalledPackagesAsUser(long flags, int handleId);

    RemoteInputStream getInstalledPackagesAsUserStream(long flags, int handleId);

    void setApplicationEnabledSetting (in InstallId installId, int newState, int flags);

    boolean grantPermission(String packageName, int handleId, String permissionId);

    boolean revokePermission(String packageName, int handleId, String permissionId);

    boolean setAppOps(String packageName, int handleId, String key, String value);
}