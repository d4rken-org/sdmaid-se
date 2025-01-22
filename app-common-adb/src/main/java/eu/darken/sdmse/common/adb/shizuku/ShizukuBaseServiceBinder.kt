package eu.darken.sdmse.common.adb.shizuku

import rikka.shizuku.ShizukuBinderWrapper

data class ShizukuBaseServiceBinder(private val original: ShizukuBinderWrapper) {
    fun pingBinder(): Boolean {
        return original.pingBinder()
    }
}
