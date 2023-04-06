package eu.darken.sdmse.common.files.local.root;

import eu.darken.sdmse.common.files.local.root.RemoteInputStream;
import eu.darken.sdmse.common.files.local.LocalPath;

interface DetailedInputSource {
    LocalPath path();
    long length();
    RemoteInputStream input();
}