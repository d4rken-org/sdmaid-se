package eu.darken.sdmse.common.files.local.root;

import eu.darken.sdmse.common.ipc.RemoteInputStream;
import eu.darken.sdmse.common.files.local.LocalPath;

interface DetailedInputSource {
    LocalPath path();
    long length();
    RemoteInputStream input();
}