package eu.darken.sdmse.common.pkgs.pkgops.root;

import eu.darken.sdmse.common.pkgs.pkgops.installer.RemoteInstallRequest;

interface PkgOpsConnection {

    int install(in RemoteInstallRequest request);

    String getUserNameForUID(int uid);

    String getGroupNameforGID(int gid);

    boolean forceStop(String packageName);

}