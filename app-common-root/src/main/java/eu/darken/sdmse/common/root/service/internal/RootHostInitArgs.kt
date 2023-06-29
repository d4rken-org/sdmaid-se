package eu.darken.sdmse.common.root.service.internal

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RootHostInitArgs(
    val packageName: String,
    val pairingCode: String,
    val waitForDebugger: Boolean = false,
    val isDebug: Boolean = false,
    val isTrace: Boolean = false,
    val isDryRun: Boolean = false,
    val recorderPath: String? = null
) : Parcelable