package eu.darken.sdmse.appcontrol.ui

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.export.AppExportType
import eu.darken.sdmse.common.pkgs.isArchived
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.isUninstalled
import eu.darken.sdmse.common.ui.layoutInflator
import eu.darken.sdmse.databinding.AppcontrolAppinfoTagViewBinding

class AppInfoTagView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = AppcontrolAppinfoTagViewBinding.inflate(layoutInflator, this)

    override fun onFinishInflate() {
        if (isInEditMode) {
            children.forEach { it.isVisible = true }
        }
        super.onFinishInflate()
    }

    fun setPkg(appInfo: AppInfo) = ui.apply {
        tagSystem.tagSystem.isVisible = appInfo.pkg.isSystemApp

        tagArchived.tagArchived.isVisible = appInfo.pkg.isArchived
        tagUninstalled.tagUninstalled.isVisible = appInfo.pkg.isUninstalled
        tagApkBase.tagApkBase.isVisible = appInfo.exportType == AppExportType.APK
        tagApkBundle.tagApkBundle.isVisible = appInfo.exportType == AppExportType.BUNDLE

        tagDisabled.tagDisabled.isVisible = !appInfo.pkg.isEnabled
        tagActive.tagActive.isVisible = appInfo.isActive == true
        isGone = children.none { it.isVisible }
    }

}