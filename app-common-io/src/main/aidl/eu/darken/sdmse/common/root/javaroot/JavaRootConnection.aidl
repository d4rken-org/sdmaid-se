package eu.darken.sdmse.common.root.javaroot;

import eu.darken.sdmse.common.files.core.local.root.FileOpsConnection;
import eu.darken.sdmse.common.pkgs.pkgops.root.PkgOpsConnection;

interface JavaRootConnection {
    String checkBase();

    FileOpsConnection getFileOps();

    PkgOpsConnection getPkgOps();
}