package eu.darken.sdmse.common.files.core.local

import android.system.Os
import android.system.StructStat
import eu.darken.rxshell.cmd.Cmd
import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.rxshell.extra.CmdHelper
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.core.Ownership
import eu.darken.sdmse.common.files.core.Permissions
import eu.darken.sdmse.common.files.core.asFile
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import java.io.File
import java.time.Instant


fun LocalPath.crumbsTo(child: LocalPath): Array<String> {
    val childPath = child.path
    val parentPath = this.path
    val pure = childPath.replaceFirst(parentPath, "")
    return pure.split(File.separatorChar)
        .filter { it.isNotEmpty() }
        .toTypedArray()
}

fun LocalPath.toCrumbs(): List<LocalPath> {
    val crumbs = mutableListOf<LocalPath>()
    crumbs.add(this)
    var parent = this.asFile().parentFile
    while (parent != null) {
        crumbs.add(0, LocalPath.build(parent))
        parent = parent.parentFile
    }
    return crumbs
}

fun LocalPath.performLookup(
    ipcFunnel: IPCFunnel? = null,
    libcoreTool: LibcoreTool? = null,
    shellSession: RxCmdShell.Session? = null
): LocalPathLookup {
    val fstat: StructStat? = try {
        Os.lstat(file.path)
    } catch (e: Exception) {
        log(LocalGateway.TAG, WARN) { "fstat failed on $this: ${e.asLog()}" }
        null
    }

    val ownership = fstat?.let {
        val uid = it.st_uid
        val gid = it.st_gid

        var userName: String? = null
        var groupName: String? = null
        if (shellSession != null) {
            val result = Cmd.builder("stat -c \"%U:%G\" ${CmdHelper.san(file.path)}").execute(shellSession)
            if (result.exitCode == Cmd.ExitCode.OK) {
                val split = result.output.first().split(":")
                userName = split[0]
                groupName = split[1]
            }
        }

        if (libcoreTool != null) {
            if (userName == null) userName = libcoreTool.getNameForUid(uid)
            if (groupName == null) groupName = libcoreTool.getNameForGid(gid)
        }

        if (ipcFunnel != null) {

        }

        Ownership(uid, gid, userName, groupName)
    }

    return LocalPathLookup(
        fileType = file.getAPathFileType(),
        lookedUp = this,
        size = file.length(),
        modifiedAt = Instant.ofEpochMilli(file.lastModified()),
        ownership = ownership,
        permissions = fstat?.let { Permissions(it.st_mode) },
        target = file.readLink()?.let { LocalPath.build(it) }
    )
}

fun LocalPath.isAncestorOf(child: LocalPath): Boolean {
    val parentPath = this.asFile().absolutePath
    val childPath = child.asFile().absolutePath

    return when (parentPath) {
        childPath -> false
        File.separator -> childPath.startsWith(parentPath)
        else -> childPath.startsWith(parentPath + File.separator)
    }
}

fun LocalPath.isParentOf(child: LocalPath): Boolean {
    return isAncestorOf(child) && child(child.name) == child
}