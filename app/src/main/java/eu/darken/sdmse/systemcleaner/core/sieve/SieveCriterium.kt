package eu.darken.sdmse.systemcleaner.core.sieve

import android.os.Parcelable

sealed interface SieveCriterium : Parcelable {
    sealed interface Mode : Parcelable
}