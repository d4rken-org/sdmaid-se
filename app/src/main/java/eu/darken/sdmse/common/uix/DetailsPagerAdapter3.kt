package eu.darken.sdmse.common.uix

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

abstract class DetailsPagerAdapter3<T> : FragmentStatePagerAdapter4<T> {

    constructor(
        activity: FragmentActivity, fm: FragmentManager
    ) : super(activity, fm)

    constructor(
        activity: FragmentActivity,
        fm: FragmentManager,
        enableSavedStates: Boolean
    ) : super(activity, fm, enableSavedStates)

}