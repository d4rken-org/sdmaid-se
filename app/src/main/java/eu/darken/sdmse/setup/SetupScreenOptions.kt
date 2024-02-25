package eu.darken.sdmse.setup

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SetupScreenOptions(
    val typeFilter: Set<SetupModule.Type>? = null,
    val isOnboarding: Boolean = false,
    val showCompleted: Boolean = false,
) : Parcelable
