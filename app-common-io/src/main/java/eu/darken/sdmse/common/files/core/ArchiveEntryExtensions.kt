package eu.darken.sdmse.common.files.core

import org.apache.commons.compress.archivers.tar.TarArchiveEntry

fun TarArchiveEntry.toHumanReadableString(): String {
    return "TarArchiveEntry(" +
            "name=${this.name}, " +
            "modified=${this.modTime}, " +
            "uid=${this.longUserId}, gid=${this.longGroupId}," +
            "userName=${this.userName}, groupName=${this.groupName}" +
            ")"
}