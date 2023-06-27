package eu.darken.sdmse.common.files.local.ipc;

import eu.darken.sdmse.common.ipc.RemoteInputStream;
import eu.darken.sdmse.common.files.local.LocalPath;

interface DetailedInputSource {
    LocalPath path();
    long length();
    RemoteInputStream input();
}