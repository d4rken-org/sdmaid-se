package eu.darken.sdmse.common.files.local.ipc;

import eu.darken.sdmse.common.ipc.RemoteFileHandle;
import eu.darken.sdmse.common.ipc.RemoteInputStream;
import eu.darken.sdmse.common.ipc.RemoteOutputStream;
import eu.darken.sdmse.common.files.local.LocalPath;
import eu.darken.sdmse.common.files.local.LocalPathLookup;
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended;
import eu.darken.sdmse.common.files.Ownership;
import eu.darken.sdmse.common.files.Permissions;

interface FileOpsConnection {

    RemoteFileHandle file(in LocalPath path, boolean readWrite);

    boolean mkdirs(in LocalPath path);
    boolean createNewFile(in LocalPath path);

    boolean canRead(in LocalPath path);
    boolean canWrite(in LocalPath path);

    boolean exists(in LocalPath path);

    boolean delete(in LocalPath path);

    RemoteInputStream listFilesStream(in LocalPath path);

    LocalPathLookup lookUp(in LocalPath path);
    RemoteInputStream lookupFilesStream(in LocalPath path);

    LocalPathLookupExtended lookUpExtended(in LocalPath path);
    List<LocalPathLookupExtended> lookupFilesExtended(in LocalPath path);
    RemoteInputStream lookupFilesExtendedStream(in LocalPath path);

    RemoteInputStream walkStream(in LocalPath path, in List<String> pathDoesNotContain);

    long du(in LocalPath path);

    boolean createSymlink(in LocalPath linkPath, in LocalPath targetPath);

    boolean setModifiedAt(in LocalPath path, in long modifiedAt);

    boolean setPermissions(in LocalPath path, in Permissions permissions);

    boolean setOwnership(in LocalPath path, in Ownership ownership);
}