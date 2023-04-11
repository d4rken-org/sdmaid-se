package eu.darken.sdmse.common.files.local

import android.system.Os
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import java.io.File

fun File.getAPathFileType(): FileType? = when {
    // Order matters!
    isSymbolicLink() -> FileType.SYMBOLIC_LINK
    isDirectory -> FileType.DIRECTORY
    isFile -> FileType.FILE
    exists() -> FileType.UNKNOWN
    else -> null
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