package eu.darken.sdmse.common.sieve

import android.os.Parcelable

sealed interface SieveCriterium : Criterium, Parcelable {
    val mode: Mode

    sealed interface Mode : Parcelable
}