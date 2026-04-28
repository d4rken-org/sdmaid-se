package eu.darken.sdmse.common.pkgs.container

import android.content.Context
import android.graphics.drawable.Drawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.pkgs.Pkg

data class PkgStub(override val id: Pkg.Id) : Pkg {
    override val label: CaString?
        get() = null
    override val icon: ((Context) -> Drawable)?
        get() = null
}

fun Pkg.Id.toStub(): PkgStub = PkgStub(this)
