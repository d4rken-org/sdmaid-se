package eu.darken.sdmse.common.shizuku;

import eu.darken.sdmse.common.files.local.root.FileOpsConnection;
import eu.darken.sdmse.common.pkgs.pkgops.root.PkgOpsConnection;
import eu.darken.sdmse.common.shell.root.ShellOpsConnection;

interface ShizukuServiceConnection {
    String checkBase();

    FileOpsConnection getFileOps();

    PkgOpsConnection getPkgOps();

    ShellOpsConnection getShellOps();
}