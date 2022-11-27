package eu.darken.sdmse.common.pkgs.picker.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PickedPkg(
    val pkg: String
) : Parcelable
