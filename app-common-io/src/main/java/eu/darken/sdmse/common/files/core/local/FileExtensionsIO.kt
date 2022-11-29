package eu.darken.sdmse.common.files.core.local

import android.system.Os
import eu.darken.sdmse.common.files.core.FileType
import eu.darken.sdmse.common.files.core.Ownership
import eu.darken.sdmse.common.files.core.Permissions
import java.io.File

fun File.asSFile(): eu.darken.sdmse.common.files.core.APath {
    return LocalPath.build(file = this)
}

fun File.getAPathFileType(): FileType = when {
    // Order matters!
    isSymbolicLink() -> FileType.SYMBOLIC_LINK
    isDirectory -> FileType.DIRECTORY
    else -> FileType.FILE
}

fun File.toLocalPath(): LocalPath = LocalPath.build(this)

fun File.setPermissions(permissions: Permissions): Boolean {
    Os.chmod(path, permissions.mode)
    return true
}

fun File.setOwnership(ownership: Ownership): Boolean {
    Os.lchown(path, ownership.userId.toInt(), ownership.groupId.toInt())
    return true
}