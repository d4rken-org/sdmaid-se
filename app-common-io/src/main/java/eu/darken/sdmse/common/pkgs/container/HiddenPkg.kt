package eu.darken.sdmse.common.pkgs.container

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.getIcon2
import eu.darken.sdmse.common.pkgs.getLabel2
import eu.darken.sdmse.common.pkgs.toPkgId

data class HiddenPkg(
    override val packageInfo: PackageInfo,
) : InstalledPkg() {

    override val id: Pkg.Id = packageInfo.packageName.toPkgId()

    private var _label: String? = null
    override fun getLabel(context: Context): String {
        _label?.let { return it }
        val newLabel = context.packageManager.getLabel2(id)
            ?: id.name
        _label = newLabel
        return newLabel
    }

    override fun getIcon(context: Context): Drawable =
        context.packageManager.getIcon2(id)
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_default_app_icon_24)!!

    override fun toString(): String = "BasicPkg(packageName=$packageName"
}