package eu.darken.sdmse.common.pkgs

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caDrawable
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.io.R
import eu.darken.sdmse.common.pkgs.features.AppStore
import kotlin.reflect.full.isSubclassOf

@Keep
sealed class AKnownPkg constructor(override val id: Pkg.Id) : Pkg {
    constructor(rawPkgId: String) : this(Pkg.Id(rawPkgId))

    @get:StringRes open val labelRes: Int? = null
    @get:DrawableRes open val iconRes: Int? = R.drawable.ic_default_app_icon_24

    override val label: CaString?
        get() = caString { context ->
            context.packageManager.getLabel2(id)?.let { return@caString it }

            labelRes?.let { return@caString context.getString(it) }

            id.name
        }

    override val icon: CaDrawable?
        get() = caDrawable { context ->
            context.packageManager.getIcon2(id)?.let { return@caDrawable it }

            iconRes
                ?.let { ContextCompat.getDrawable(context, it) }
                ?.let { return@caDrawable it }

            ContextCompat.getDrawable(context, R.drawable.ic_default_app_icon_24)!!
        }

    object AndroidSystem : AKnownPkg("android") {
        override val labelRes: Int = R.string.apps_known_android_system_label
    }

    object GooglePlay : AKnownPkg("com.android.vending"), AppStore {
        override val labelRes: Int = R.string.apps_known_installer_gplay_label
        override val iconRes: Int = R.drawable.ic_baseline_gplay_24
        override val urlGenerator: ((Pkg.Id) -> String) = {
            "https://play.google.com/store/apps/details?id=${it.name}"
        }
    }

    object VivoAppStore : AKnownPkg("com.vivo.appstore"), AppStore {
        override val labelRes: Int = R.string.apps_known_installer_vivo_label
    }

    object OppoMarket : AKnownPkg("com.oppo.market"), AppStore {
        override val labelRes: Int = R.string.apps_known_installer_oppo_label
    }

    object HuaweiAppGallery : AKnownPkg("com.huawei.appmarket"), AppStore {
        override val labelRes: Int = R.string.apps_known_installer_huawei_label
    }

    object SamsungAppStore : AKnownPkg("com.sec.android.app.samsungapps"), AppStore {
        override val labelRes: Int = R.string.apps_known_installer_samsung_label
    }

    object XiaomiAppStore : AKnownPkg("com.xiaomi.mipicks"), AppStore {
        override val labelRes: Int = R.string.apps_known_installer_xiaomi_label
    }

    companion object {
        // Without lazy there is an NPE: https://youtrack.jetbrains.com/issue/KT-25957
        val values: List<AKnownPkg> by lazy {
            AKnownPkg::class.nestedClasses
                .filter { clazz -> clazz.isSubclassOf(AKnownPkg::class) }
                .map { clazz -> clazz.objectInstance }
                .filterIsInstance<AKnownPkg>()
        }

        val APP_STORES by lazy { values.filterIsInstance<AppStore>() }
        val OEM_STORES by lazy { APP_STORES - GooglePlay }
    }
}

fun Pkg.Id.toKnownPkg(): Pkg? = AKnownPkg.values.singleOrNull { it.id == this@toKnownPkg }