package eu.darken.sdmse.common.shell.ipc

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShellOpsResult(
    val exitCode: Int,
    val output: List<String>,
    val errors: List<String>,
) : Parcelable {

    val isSuccess: Boolean
        get() = exitCode == 0
}