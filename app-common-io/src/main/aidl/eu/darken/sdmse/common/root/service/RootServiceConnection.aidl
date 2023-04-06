package eu.darken.sdmse.common.root.service;

import eu.darken.sdmse.common.files.local.root.FileOpsConnection;
import eu.darken.sdmse.common.pkgs.pkgops.root.PkgOpsConnection;

interface RootServiceConnection {
    String checkBase();

    FileOpsConnection getFileOps();

    PkgOpsConnection getPkgOps();
}