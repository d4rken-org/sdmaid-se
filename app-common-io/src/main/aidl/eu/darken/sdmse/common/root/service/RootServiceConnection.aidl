package eu.darken.sdmse.common.root.service;

import eu.darken.sdmse.common.files.local.ipc.FileOpsConnection;
import eu.darken.sdmse.common.pkgs.pkgops.ipc.PkgOpsConnection;
import eu.darken.sdmse.common.shell.ipc.ShellOpsConnection;

interface RootServiceConnection {
    String checkBase();

    FileOpsConnection getFileOps();

    PkgOpsConnection getPkgOps();

    ShellOpsConnection getShellOps();
}