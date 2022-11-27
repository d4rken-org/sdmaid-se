package eu.darken.sdmse.common.pkgs.picker.ui

import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.Keep
import eu.darken.sdmse.common.pkgs.picker.core.PickedPkg
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Keep @Parcelize
data class PkgPickerResult(
    val options: PkgPickerOptions,
    val error: Throwable? = null,
    val selection: Set<PickedPkg>? = null,
    val payload: Bundle = Bundle()
) : Parcelable {

    @IgnoredOnParcel val isCanceled: Boolean = error == null && selection == null
    @IgnoredOnParcel val isSuccess: Boolean = error == null && selection != null
    @IgnoredOnParcel val isFailed: Boolean = error != null
}