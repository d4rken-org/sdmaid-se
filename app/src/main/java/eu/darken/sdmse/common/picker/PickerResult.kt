package eu.darken.sdmse.common.picker

import android.os.Bundle
import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import kotlinx.parcelize.Parcelize

@Parcelize
data class PickerResult(
    val selectedPaths: Set<APath>,
) : Parcelable {

    fun toBundle() = Bundle().apply {
        putParcelable(BUNDLE_KEY, this@PickerResult)
    }

    companion object {
        private const val BUNDLE_KEY = "result"
        fun fromBundle(bundle: Bundle): PickerResult {
            @Suppress("DEPRECATION")
            return bundle.getParcelable(BUNDLE_KEY)!!
        }
    }
}
