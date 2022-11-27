package eu.darken.sdmse.common.pkgs.pkgops.installer;

import eu.darken.sdmse.common.files.core.local.root.DetailedInputSource;

interface RemoteInstallRequest {

    String getPackageName();

    List getApkInputs();

}