package eu.darken.sdmse.common.shizuku;

import eu.darken.sdmse.common.files.local.ipc.FileOpsConnection;
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsConnection;
import eu.darken.sdmse.common.shell.ipc.ShellOpsConnection;

interface ShizukuServiceConnection {
    String checkBase();

    FileOpsConnection getFileOps();

    PkgOpsConnection getPkgOps();

    ShellOpsConnection getShellOps();
}