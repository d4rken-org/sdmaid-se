package eu.darken.sdmse.common.pkgs

import android.content.Context
import android.content.Intent
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.AppStore
import javax.inject.Inject


@Reusable
class AppStoreTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
) {

    fun openAppStoreFor(target: Pkg, installer: Pkg) {
        if (hasApiLevel(24)) {
            val generic = Intent(Intent.ACTION_SHOW_APP_INFO).apply {
                setPackage(installer.packageName)
            }
            val specific = generic.resolveToActivity()?.apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, target.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (specific != null) {
                log { "Using resovled app store intent: $specific" }
                context.startActivity(specific)
                return
            }
        }
        if (installer is AppStore) {
            installer.urlGenerator?.invoke(target.id)?.let {
                log { "Using urlgenerator from known app store: $it" }
                webpageTool.open(it)
                return
            }
        }
        log(WARN) { "No known way to open $target in $installer" }
    }

    private fun Intent.resolveToActivity(): Intent? = context.packageManager.resolveActivity(this, 0)?.let { result ->
        Intent(this.action)
            .apply { setClassName(result.activityInfo.packageName, result.activityInfo.name) }
    }
}