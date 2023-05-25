package eu.darken.sdmse.corpsefinder.core.watcher

import android.os.Parcelable
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

sealed interface ExternalWatcherTask : Parcelable {
    @Parcelize
    data class Delete(
        val target: Pkg.Id
    ) : ExternalWatcherTask
}