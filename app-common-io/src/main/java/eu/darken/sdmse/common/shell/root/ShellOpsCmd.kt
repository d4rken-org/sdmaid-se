package eu.darken.sdmse.common.shell.root

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShellOpsCmd(
    val cmds: List<String>,
) : Parcelable {
    constructor(vararg cmds: String) : this(cmds.toList())
}
