package eu.darken.sdmse.common.adb;

import eu.darken.sdmse.common.files.local.ipc.FileOpsConnection;
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsConnection;
import eu.darken.sdmse.common.shell.ipc.ShellOpsConnection;

interface AdbServiceConnection {
    String checkBase();

    FileOpsConnection getFileOps();

    PkgOpsConnection getPkgOps();

    ShellOpsConnection getShellOps();
}