package eu.darken.sdmse.systemcleaner.core.sieve

import android.os.Parcelable

sealed interface SieveCriterium : Parcelable {
    val mode: Mode

    sealed interface Mode : Parcelable
}