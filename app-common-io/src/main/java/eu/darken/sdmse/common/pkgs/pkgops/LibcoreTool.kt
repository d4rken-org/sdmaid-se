package eu.darken.sdmse.common.pkgs.pkgops

import dagger.Reusable
import javax.inject.Inject

@Reusable
class LibcoreTool @Inject constructor() {

    private fun getLibCoreOS(): Any? {
        val clazz = Class.forName("libcore.io.Libcore")
        val field = clazz.getDeclaredField("os")
        if (!field.isAccessible) field.isAccessible = true

        val os = field[null]
        return os
    }

    fun getNameForUid(uid: Int): String? {
        try {
            val os = getLibCoreOS()
            if (os == null) return null

            val getpwuid = os.javaClass.getMethod("getpwuid", Int::class.javaPrimitiveType)
            if (!getpwuid.isAccessible) getpwuid.isAccessible = true

            val passwd = getpwuid.invoke(os, uid)
            if (passwd == null) return null

            val pwName = passwd.javaClass.getDeclaredField("pw_name")
            if (!pwName.isAccessible) pwName.isAccessible = true

            return pwName[passwd] as String
        } catch (e: Exception) {
//            Timber.v(e, "getNameForUid failed")
            return null
        }
    }

    fun getNameForGid(gid: Int): String? {
        try {
            val os = getLibCoreOS()
            if (os == null) return null

            val getgrgid = os.javaClass.getMethod("getgrgid", Int::class.javaPrimitiveType)
            if (!getgrgid.isAccessible) getgrgid.isAccessible = true

            val group = getgrgid.invoke(os, gid)
            if (group == null) return null

            val grName = group.javaClass.getDeclaredField("gr_name")
            if (!grName.isAccessible) grName.isAccessible = true

            return grName[group] as String
        } catch (e: Exception) {
//            Timber.v(e, "getNameForGid failed")
            return null
        }
    }
}