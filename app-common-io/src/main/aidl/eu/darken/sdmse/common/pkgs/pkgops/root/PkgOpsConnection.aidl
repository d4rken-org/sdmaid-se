package eu.darken.sdmse.common.pkgs.pkgops.root;

interface PkgOpsConnection {

    String getUserNameForUID(int uid);

    String getGroupNameforGID(int gid);

    boolean forceStop(String packageName);

}