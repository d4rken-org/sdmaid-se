package eu.darken.sdmse.common.shell.root

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShellOpsResult(
    val exitCode: Int,
    val output: List<String>,
    val errors: List<String>,
) : Parcelable