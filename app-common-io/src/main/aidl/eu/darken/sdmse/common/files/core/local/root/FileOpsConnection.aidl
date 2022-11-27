package eu.darken.sdmse.common.files.core.local.root;

import eu.darken.sdmse.common.files.core.local.root.RemoteInputStream;
import eu.darken.sdmse.common.files.core.local.root.RemoteOutputStream;
import eu.darken.sdmse.common.files.core.local.LocalPath;
import eu.darken.sdmse.common.files.core.local.LocalPathLookup;
import eu.darken.sdmse.common.files.core.Ownership;
import eu.darken.sdmse.common.files.core.Permissions;

interface FileOpsConnection {

    RemoteInputStream readFile(in LocalPath path);
    RemoteOutputStream writeFile(in LocalPath path);

    boolean mkdirs(in LocalPath path);
    boolean createNewFile(in LocalPath path);

    boolean canRead(in LocalPath path);
    boolean canWrite(in LocalPath path);

    boolean exists(in LocalPath path);

    boolean delete(in LocalPath path);

    List<LocalPath> listFiles(in LocalPath path);

    LocalPathLookup lookUp(in LocalPath path);
    List<LocalPathLookup> lookupFiles(in LocalPath path);

    boolean createSymlink(in LocalPath linkPath, in LocalPath targetPath);

    boolean setModifiedAt(in LocalPath path, in long modifiedAt);

    boolean setPermissions(in LocalPath path, in Permissions permissions);

    boolean setOwnership(in LocalPath path, in Ownership ownership);
}