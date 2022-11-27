package eu.darken.sdmse.common.files.core.local.root;

import eu.darken.sdmse.common.files.core.local.root.RemoteInputStream;
import eu.darken.sdmse.common.files.core.local.LocalPath;

interface DetailedInputSource {
    LocalPath path();
    long length();
    RemoteInputStream input();
}