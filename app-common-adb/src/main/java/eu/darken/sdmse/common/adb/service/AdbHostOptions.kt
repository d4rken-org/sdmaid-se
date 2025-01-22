package eu.darken.sdmse.common.adb.service

import android.os.Parcelable
import eu.darken.sdmse.common.BuildConfigWrap
import kotlinx.parcelize.Parcelize

@Parcelize
data class AdbHostOptions(
    val isDebug: Boolean = BuildConfigWrap.DEBUG,
    val isTrace: Boolean = false,
    val isDryRun: Boolean = false,
    val recorderPath: String? = null
) : Parcelable